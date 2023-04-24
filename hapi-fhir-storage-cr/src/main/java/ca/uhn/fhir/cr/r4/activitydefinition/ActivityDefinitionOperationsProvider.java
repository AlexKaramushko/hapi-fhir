package ca.uhn.fhir.cr.r4.activitydefinition;

/*-
 * #%L
 * HAPI FHIR - Clinical Reasoning
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

import ca.uhn.fhir.cr.common.IRepositoryFactory;
import ca.uhn.fhir.cr.r4.IActivityDefinitionProcessorFactory;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.provider.ProviderConstants;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ActivityDefinition;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Endpoint;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ActivityDefinitionOperationsProvider {
	@Autowired
	IRepositoryFactory myRepositoryFactory;
	@Autowired
	IActivityDefinitionProcessorFactory myR4ActivityDefinitionProcessorFactory;

	/**
	 * Implements the <a href=
	 * "http://www.hl7.org/fhir/activitydefinition-operation-apply.html">$apply</a>
	 * operation found in the
	 * <a href="http://www.hl7.org/fhir/clinicalreasoning-module.html">FHIR Clinical
	 * Reasoning Module</a>. This implementation aims to be compatible with the CPG
	 * IG.
	 *
	 * @param theId                  The id of the ActivityDefinition to apply
	 * @param theCanonical           The canonical identifier for the ActivityDefinition to apply (optionally version-specific)
	 * @param theActivityDefinition  The ActivityDefinition to be applied
	 * @param theSubject             The subject(s) that is/are the target of the activity definition to be applied.
	 * @param theEncounter           The encounter in context
	 * @param thePractitioner        The practitioner in context
	 * @param theOrganization        The organization in context
	 * @param theUserType            The type of user initiating the request, e.g. patient, healthcare provider,
	 *                               or specific type of healthcare provider (physician, nurse, etc.)
	 * @param theUserLanguage        Preferred language of the person using the system
	 * @param theUserTaskContext     The task the system user is performing, e.g. laboratory results review,
	 *                               medication list review, etc. This information can be used to tailor decision
	 *                               support outputs, such as recommended information resources
	 * @param theSetting             The current setting of the request (inpatient, outpatient, etc.)
	 * @param theSettingContext      Additional detail about the setting of the request, if any
	 * @param theParameters          Any input parameters defined in libraries referenced by the ActivityDefinition.
	 * @param theDataEndpoint        An endpoint to use to access data referenced by retrieve operations in libraries
	 *                               referenced by the ActivityDefinition.
	 * @param theContentEndpoint     An endpoint to use to access content (i.e. libraries) referenced by the ActivityDefinition.
	 * @param theTerminologyEndpoint An endpoint to use to access terminology (i.e. valuesets, codesystems, and membership testing)
	 *                               referenced by the ActivityDefinition.
	 * @param theRequestDetails      The details (such as tenant) of this request. Usually
	 *                               autopopulated HAPI.
	 * @return The resource that is the result of applying the definition
	 */
	@Operation(name = ProviderConstants.CR_OPERATION_APPLY, idempotent = true, type = ActivityDefinition.class)
	public IBaseResource apply(@IdParam IdType theId,
										@OperationParam(name = "canonical") String theCanonical,
										@OperationParam(name = "activityDefinition") ActivityDefinition theActivityDefinition,
										@OperationParam(name = "subject") String theSubject,
										@OperationParam(name = "encounter") String theEncounter,
										@OperationParam(name = "practitioner") String thePractitioner,
										@OperationParam(name = "organization") String theOrganization,
										@OperationParam(name = "userType") String theUserType,
										@OperationParam(name = "userLanguage") String theUserLanguage,
										@OperationParam(name = "userTaskContext") String theUserTaskContext,
										@OperationParam(name = "setting") String theSetting,
										@OperationParam(name = "settingContext") String theSettingContext,
										@OperationParam(name = "parameters") Parameters theParameters,
										@OperationParam(name = "dataEndpoint") Endpoint theDataEndpoint,
										@OperationParam(name = "contentEndpoint") Endpoint theContentEndpoint,
										@OperationParam(name = "terminologyEndpoint") Endpoint theTerminologyEndpoint,
										RequestDetails theRequestDetails) throws InternalErrorException, FHIRException {
		return myR4ActivityDefinitionProcessorFactory
			.create(myRepositoryFactory.create(theRequestDetails))
			.apply(theId,
				new CanonicalType(theCanonical),
				theActivityDefinition,
				theSubject,
				theEncounter,
				thePractitioner,
				theOrganization,
				theUserType,
				theUserLanguage,
				theUserTaskContext,
				theSetting,
				theSettingContext,
				theParameters,
				theDataEndpoint,
				theContentEndpoint,
				theTerminologyEndpoint);
	}

	@Operation(name = ProviderConstants.CR_OPERATION_APPLY, idempotent = true, type = ActivityDefinition.class)
	public IBaseResource apply(@OperationParam(name = "canonical") String theCanonical,
										@OperationParam(name = "activityDefinition") ActivityDefinition theActivityDefinition,
										@OperationParam(name = "subject") String theSubject,
										@OperationParam(name = "encounter") String theEncounter,
										@OperationParam(name = "practitioner") String thePractitioner,
										@OperationParam(name = "organization") String theOrganization,
										@OperationParam(name = "userType") String theUserType,
										@OperationParam(name = "userLanguage") String theUserLanguage,
										@OperationParam(name = "userTaskContext") String theUserTaskContext,
										@OperationParam(name = "setting") String theSetting,
										@OperationParam(name = "settingContext") String theSettingContext,
										@OperationParam(name = "parameters") Parameters theParameters,
										@OperationParam(name = "dataEndpoint") Endpoint theDataEndpoint,
										@OperationParam(name = "contentEndpoint") Endpoint theContentEndpoint,
										@OperationParam(name = "terminologyEndpoint") Endpoint theTerminologyEndpoint,
										RequestDetails theRequestDetails) throws InternalErrorException, FHIRException {
		return myR4ActivityDefinitionProcessorFactory
			.create(myRepositoryFactory.create(theRequestDetails))
			.apply(null,
				new CanonicalType(theCanonical),
				theActivityDefinition,
				theSubject,
				theEncounter,
				thePractitioner,
				theOrganization,
				theUserType,
				theUserLanguage,
				theUserTaskContext,
				theSetting,
				theSettingContext,
				theParameters,
				theDataEndpoint,
				theContentEndpoint,
				theTerminologyEndpoint);
	}
}
