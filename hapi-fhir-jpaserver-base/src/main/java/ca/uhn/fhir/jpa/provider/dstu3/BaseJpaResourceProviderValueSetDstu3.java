package ca.uhn.fhir.jpa.provider.dstu3;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2022 Smile CDR, Inc.
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

import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDaoValueSet;
import ca.uhn.fhir.jpa.model.util.JpaConstants;
import ca.uhn.fhir.jpa.provider.BaseJpaResourceProviderValueSetDstu2;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.IntegerType;
import org.hl7.fhir.dstu3.model.Parameters;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.UriType;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IPrimitiveType;

import javax.servlet.http.HttpServletRequest;

import static ca.uhn.fhir.jpa.provider.r4.BaseJpaResourceProviderValueSetR4.createValueSetExpansionOptions;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class BaseJpaResourceProviderValueSetDstu3 extends JpaResourceProviderDstu3<ValueSet> {

	@Operation(name = JpaConstants.OPERATION_EXPAND, idempotent = true)
	public ValueSet expand(
		HttpServletRequest theServletRequest,
		@IdParam(optional = true) IdType theId,
		@OperationParam(name = "valueSet", min = 0, max = 1) ValueSet theValueSet,
		// Note: url is correct and identifier is not, but identifier was only added as
		// of 3.1.0 so we'll leave url for now. See: https://groups.google.com/d/msgid/hapi-fhir/CAN2Cfy8kW%2BAOkgC6VjPsU3gRCpExCNZBmJdi-k5R_TWeyWH4tA%40mail.gmail.com?utm_medium=email&utm_source=footer
		@OperationParam(name = "url", min = 0, max = 1) UriType theUrl,
		@OperationParam(name = "valueSetVersion", min = 0, max = 1) StringType theValueSetVersion,
		@OperationParam(name = "identifier", min = 0, max = 1) UriType theIdentifier,
		@OperationParam(name = "filter", min = 0, max = 1) StringType theFilter,
		@OperationParam(name = "offset", min = 0, max = 1) IntegerType theOffset,
		@OperationParam(name = "count", min = 0, max = 1) IntegerType theCount,
		@OperationParam(name = JpaConstants.OPERATION_EXPAND_PARAM_INCLUDE_HIERARCHY, min = 0, max = 1, typeName = "boolean") IPrimitiveType<Boolean> theIncludeHierarchy,
		RequestDetails theRequestDetails) {

		boolean haveId = theId != null && theId.hasIdPart();
		UriType url = theIdentifier;
		if (theUrl != null && isNotBlank(theUrl.getValue())) {
			url = theUrl;
		}

		boolean haveIdentifier = url != null && isNotBlank(url.getValue());
		boolean haveValueSet = theValueSet != null && !theValueSet.isEmpty();
		boolean haveValueSetVersion = theValueSetVersion != null && !theValueSetVersion.isEmpty();

		if (!haveId && !haveIdentifier && !haveValueSet) {
			throw new InvalidRequestException("$expand operation at the type level (no ID specified) requires an identifier or a valueSet as a part of the request.");
		}

		if (moreThanOneTrue(haveId, haveIdentifier, haveValueSet)) {
			throw new InvalidRequestException("$expand must EITHER be invoked at the instance level, or have an identifier specified, or have a ValueSet specified. Can not combine these options.");
		}

		ValueSetExpansionOptions options = createValueSetExpansionOptions(myDaoConfig, theOffset, theCount, theIncludeHierarchy, theFilter);

		startRequest(theServletRequest);
		try {
			IFhirResourceDaoValueSet<ValueSet, Coding, CodeableConcept> dao = (IFhirResourceDaoValueSet<ValueSet, Coding, CodeableConcept>) getDao();
			if (haveId) {
				return dao.expand(theId, options, theRequestDetails);
			} else if (haveIdentifier) {
				if (haveValueSetVersion) {
					return dao.expandByIdentifier(url.getValue() + "|" + theValueSetVersion.getValue(), options);
				} else {
					return dao.expandByIdentifier(url.getValue(), options);
				}
			} else {
				return dao.expand(theValueSet, options);
			}
		} finally {
			endRequest(theServletRequest);
		}
	}


	@Operation(name = JpaConstants.OPERATION_VALIDATE_CODE, idempotent = true, returnParameters = {
		@OperationParam(name = "result", type = BooleanType.class, min = 1),
		@OperationParam(name = "message", type = StringType.class),
		@OperationParam(name = "display", type = StringType.class)
	})
	public Parameters validateCode(
		HttpServletRequest theServletRequest,
		@IdParam(optional = true) IdType theId,
		@OperationParam(name = "identifier", min = 0, max = 1) UriType theValueSetIdentifier,
		@OperationParam(name = "url", min = 0, max = 1) UriType theValueSetUrl,
		@OperationParam(name = "valueSetVersion", min = 0, max = 1) StringType theValueSetVersion,
		@OperationParam(name = "code", min = 0, max = 1) CodeType theCode,
		@OperationParam(name = "system", min = 0, max = 1) UriType theSystem,
		@OperationParam(name = "systemVersion", min = 0, max = 1) StringType theSystemVersion,
		@OperationParam(name = "display", min = 0, max = 1) StringType theDisplay,
		@OperationParam(name = "coding", min = 0, max = 1) Coding theCoding,
		@OperationParam(name = "codeableConcept", min = 0, max = 1) CodeableConcept theCodeableConcept,
		RequestDetails theRequestDetails
	) {

		UriType url = theValueSetIdentifier;
		if (theValueSetUrl != null && isNotBlank(theValueSetUrl.getValue())) {
			url = theValueSetUrl;
		}

		startRequest(theServletRequest);
		try {
			IFhirResourceDaoValueSet<ValueSet, Coding, CodeableConcept> dao = (IFhirResourceDaoValueSet<ValueSet, Coding, CodeableConcept>) getDao();
			UriType valueSetIdentifier;
			if (theValueSetVersion != null) {
				valueSetIdentifier = new UriType(url.getValue() + "|" + theValueSetVersion);
			} else {
				valueSetIdentifier = url;
			}
			UriType codeSystemIdentifier;
			if (theSystemVersion != null) {
				codeSystemIdentifier = new UriType(theSystem.getValue() + "|" + theSystemVersion);
			} else {
				codeSystemIdentifier = theSystem;
			}
			IValidationSupport.CodeValidationResult result = dao.validateCode(valueSetIdentifier, theId, theCode, codeSystemIdentifier, theDisplay, theCoding, theCodeableConcept, theRequestDetails);
			return (Parameters) BaseJpaResourceProviderValueSetDstu2.toValidateCodeResult(getContext(), result);
		} finally {
			endRequest(theServletRequest);
		}
	}


	private static boolean moreThanOneTrue(boolean... theBooleans) {
		boolean haveOne = false;
		for (boolean next : theBooleans) {
			if (next) {
				if (haveOne) {
					return true;
				} else {
					haveOne = true;
				}
			}
		}
		return false;
	}


}
