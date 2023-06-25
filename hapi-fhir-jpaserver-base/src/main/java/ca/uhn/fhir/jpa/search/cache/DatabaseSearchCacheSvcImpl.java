/*-
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2023 Smile CDR, Inc.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package ca.uhn.fhir.jpa.search.cache;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;
import ca.uhn.fhir.jpa.dao.data.ISearchDao;
import ca.uhn.fhir.jpa.dao.data.ISearchIncludeDao;
import ca.uhn.fhir.jpa.dao.data.ISearchResultDao;
import ca.uhn.fhir.jpa.dao.tx.HapiTransactionService;
import ca.uhn.fhir.jpa.dao.tx.IHapiTransactionService;
import ca.uhn.fhir.jpa.entity.Search;
import ca.uhn.fhir.jpa.model.search.SearchStatusEnum;
import ca.uhn.fhir.system.HapiSystemProperties;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.DateUtils;
import org.hl7.fhir.dstu3.model.InstantType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public class DatabaseSearchCacheSvcImpl implements ISearchCacheSvc {
	/*
	* Be careful increasing this number! We use the number of params here in a
	* DELETE FROM foo WHERE params IN (term,term,term...)
	* type query and this can fail if we have 1000s of params
	*/
	public static final int DEFAULT_MAX_RESULTS_TO_DELETE_IN_ONE_STMT = 500;
	public static final int DEFAULT_MAX_RESULTS_TO_DELETE_IN_ONE_PAS = 20000;
	public static final long SEARCH_CLEANUP_JOB_INTERVAL_MILLIS = DateUtils.MILLIS_PER_MINUTE;
	public static final int DEFAULT_MAX_DELETE_CANDIDATES_TO_FIND = 2000;
	private static final Logger ourLog = LoggerFactory.getLogger(DatabaseSearchCacheSvcImpl.class);
	private static int ourMaximumResultsToDeleteInOneStatement =
				DEFAULT_MAX_RESULTS_TO_DELETE_IN_ONE_STMT;
	private static int ourMaximumResultsToDeleteInOnePass =
				DEFAULT_MAX_RESULTS_TO_DELETE_IN_ONE_PAS;
	private static int ourMaximumSearchesToCheckForDeletionCandidacy =
				DEFAULT_MAX_DELETE_CANDIDATES_TO_FIND;
	private static Long ourNowForUnitTests;
	/*
	* We give a bit of extra leeway just to avoid race conditions where a query result
	* is being reused (because a new client request came in with the same params) right before
	* the result is to be deleted
	*/
	private long myCutoffSlack = SEARCH_CLEANUP_JOB_INTERVAL_MILLIS;
	@Autowired private ISearchDao mySearchDao;
	@Autowired private ISearchResultDao mySearchResultDao;
	@Autowired private ISearchIncludeDao mySearchIncludeDao;
	@Autowired private IHapiTransactionService myTransactionService;
	@Autowired private JpaStorageSettings myStorageSettings;

	@VisibleForTesting
	public void setCutoffSlackForUnitTest(long theCutoffSlack) {
		myCutoffSlack = theCutoffSlack;
	}

	@Override
	public Search save(Search theSearch, RequestPartitionId theRequestPartitionId) {
		return myTransactionService
					.withSystemRequestOnPartition(theRequestPartitionId)
					.execute(() -> mySearchDao.save(theSearch));
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRED)
	public Optional<Search> fetchByUuid(String theUuid, RequestPartitionId theRequestPartitionId) {
		Validate.notBlank(theUuid);
		return myTransactionService
					.withSystemRequestOnPartition(theRequestPartitionId)
					.execute(() -> mySearchDao.findByUuidAndFetchIncludes(theUuid));
	}

	void setSearchDaoForUnitTest(ISearchDao theSearchDao) {
		mySearchDao = theSearchDao;
	}

	void setTransactionServiceForUnitTest(IHapiTransactionService theTransactionService) {
		myTransactionService = theTransactionService;
	}

	@Override
	public Optional<Search> tryToMarkSearchAsInProgress(
				Search theSearch, RequestPartitionId theRequestPartitionId) {
		ourLog.trace(
					"Going to try to change search status from {} to {}",
					theSearch.getStatus(),
					SearchStatusEnum.LOADING);
		try {

				return myTransactionService
						.withSystemRequest()
						.withRequestPartitionId(theRequestPartitionId)
						.withPropagation(Propagation.REQUIRES_NEW)
						.execute(
									t -> {
										Search search =
													mySearchDao.findById(theSearch.getId()).orElse(theSearch);

										if (search.getStatus() != SearchStatusEnum.PASSCMPLET) {
												throw new IllegalStateException(
														Msg.code(1167)
																	+ "Can't change to LOADING because state is "
																	+ search.getStatus());
										}
										search.setStatus(SearchStatusEnum.LOADING);
										Search newSearch = mySearchDao.save(search);
										return Optional.of(newSearch);
									});
		} catch (Exception e) {
				ourLog.warn("Failed to activate search: {}", e.toString());
				ourLog.trace("Failed to activate search", e);
				return Optional.empty();
		}
	}

	@Override
	public Optional<Search> findCandidatesForReuse(
				String theResourceType,
				String theQueryString,
				Instant theCreatedAfter,
				RequestPartitionId theRequestPartitionId) {
		HapiTransactionService.requireTransaction();

		String queryString =
					Search.createSearchQueryStringForStorage(theQueryString, theRequestPartitionId);

		int hashCode = queryString.hashCode();
		Collection<Search> candidates =
					mySearchDao.findWithCutoffOrExpiry(
								theResourceType, hashCode, Date.from(theCreatedAfter));

		for (Search nextCandidateSearch : candidates) {
				// We should only reuse our search if it was created within the permitted window
				// Date.after() is unreliable.  Instant.isAfter() always works.
				if (queryString.equals(nextCandidateSearch.getSearchQueryString())
						&& nextCandidateSearch.getCreated().toInstant().isAfter(theCreatedAfter)) {
					return Optional.of(nextCandidateSearch);
				}
		}

		return Optional.empty();
	}

	@Override
	public void pollForStaleSearchesAndDeleteThem(RequestPartitionId theRequestPartitionId) {
		HapiTransactionService.noTransactionAllowed();

		if (!myStorageSettings.isExpireSearchResults()) {
				return;
		}

		long cutoffMillis = myStorageSettings.getExpireSearchResultsAfterMillis();
		if (myStorageSettings.getReuseCachedSearchResultsForMillis() != null) {
				cutoffMillis = cutoffMillis + myStorageSettings.getReuseCachedSearchResultsForMillis();
		}
		final Date cutoff = new Date((now() - cutoffMillis) - myCutoffSlack);

		if (ourNowForUnitTests != null) {
				ourLog.info(
						"Searching for searches which are before {} - now is {}",
						new InstantType(cutoff),
						new InstantType(new Date(now())));
		}

		ourLog.debug("Searching for searches which are before {}", cutoff);

		// Mark searches as deleted if they should be
		final Slice<Long> toMarkDeleted =
					myTransactionService
								.withSystemRequestOnPartition(theRequestPartitionId)
								.execute(
										theStatus ->
													mySearchDao.findWhereCreatedBefore(
																cutoff,
																new Date(),
																PageRequest.of(
																		0,
																		ourMaximumSearchesToCheckForDeletionCandidacy)));
		assert toMarkDeleted != null;
		for (final Long nextSearchToDelete : toMarkDeleted) {
				ourLog.debug("Deleting search with PID {}", nextSearchToDelete);
				myTransactionService
						.withSystemRequest()
						.withRequestPartitionId(theRequestPartitionId)
						.execute(
									t -> {
										mySearchDao.updateDeleted(nextSearchToDelete, true);
										return null;
									});
		}

		// Delete searches that are marked as deleted
		final Slice<Long> toDelete =
					myTransactionService
								.withSystemRequestOnPartition(theRequestPartitionId)
								.execute(
										theStatus ->
													mySearchDao.findDeleted(
																PageRequest.of(
																		0,
																		ourMaximumSearchesToCheckForDeletionCandidacy)));
		assert toDelete != null;
		for (final Long nextSearchToDelete : toDelete) {
				ourLog.debug("Deleting search with PID {}", nextSearchToDelete);
				myTransactionService
						.withSystemRequest()
						.withRequestPartitionId(theRequestPartitionId)
						.execute(
									t -> {
										deleteSearch(nextSearchToDelete);
										return null;
									});
		}

		int count = toDelete.getContent().size();
		if (count > 0) {
				if (ourLog.isDebugEnabled() || HapiSystemProperties.isTestModeEnabled()) {
					Long total =
								myTransactionService
										.withSystemRequest()
										.withRequestPartitionId(theRequestPartitionId)
										.execute(t -> mySearchDao.count());
					ourLog.debug("Deleted {} searches, {} remaining", count, total);
				}
		}
	}

	private void deleteSearch(final Long theSearchPid) {
		mySearchDao
					.findById(theSearchPid)
					.ifPresent(
								searchToDelete -> {
									mySearchIncludeDao.deleteForSearch(searchToDelete.getId());

									/*
									* Note, we're only deleting up to 500 results in an individual search here. This
									* is to prevent really long running transactions in cases where there are
									* huge searches with tons of results in them. By the time we've gotten here
									* we have marked the parent Search entity as deleted, so it's not such a
									* huge deal to be only partially deleting search results. They'll get deleted
									* eventually
									*/
									int max = ourMaximumResultsToDeleteInOnePass;
									Slice<Long> resultPids =
												mySearchResultDao.findForSearch(
														PageRequest.of(0, max), searchToDelete.getId());
									if (resultPids.hasContent()) {
										List<List<Long>> partitions =
													Lists.partition(
																resultPids.getContent(),
																ourMaximumResultsToDeleteInOneStatement);
										for (List<Long> nextPartition : partitions) {
												mySearchResultDao.deleteByIds(nextPartition);
										}
									}

									// Only delete if we don't have results left in this search
									if (resultPids.getNumberOfElements() < max) {
										ourLog.debug(
													"Deleting search {}/{} - Created[{}]",
													searchToDelete.getId(),
													searchToDelete.getUuid(),
													new InstantType(searchToDelete.getCreated()));
										mySearchDao.deleteByPid(searchToDelete.getId());
									} else {
										ourLog.debug(
													"Purged {} search results for deleted search {}/{}",
													resultPids.getSize(),
													searchToDelete.getId(),
													searchToDelete.getUuid());
									}
								});
	}

	@VisibleForTesting
	public static void setMaximumSearchesToCheckForDeletionCandidacyForUnitTest(
				int theMaximumSearchesToCheckForDeletionCandidacy) {
		ourMaximumSearchesToCheckForDeletionCandidacy =
					theMaximumSearchesToCheckForDeletionCandidacy;
	}

	@VisibleForTesting
	public static void setMaximumResultsToDeleteInOnePassForUnitTest(
				int theMaximumResultsToDeleteInOnePass) {
		ourMaximumResultsToDeleteInOnePass = theMaximumResultsToDeleteInOnePass;
	}

	@VisibleForTesting
	public static void setMaximumResultsToDeleteForUnitTest(int theMaximumResultsToDelete) {
		ourMaximumResultsToDeleteInOneStatement = theMaximumResultsToDelete;
	}

	/** This is for unit tests only, do not call otherwise */
	@VisibleForTesting
	public static void setNowForUnitTests(Long theNowForUnitTests) {
		ourNowForUnitTests = theNowForUnitTests;
	}

	private static long now() {
		if (ourNowForUnitTests != null) {
				return ourNowForUnitTests;
		}
		return System.currentTimeMillis();
	}
}
