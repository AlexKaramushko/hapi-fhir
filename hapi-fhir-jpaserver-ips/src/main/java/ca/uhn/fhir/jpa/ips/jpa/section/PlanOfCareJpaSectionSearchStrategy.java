/*-
 * #%L
 * HAPI FHIR JPA Server - International Patient Summary (IPS)
 * %%
 * Copyright (C) 2014 - 2024 Smile CDR, Inc.
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
package ca.uhn.fhir.jpa.ips.jpa.section;

import ca.uhn.fhir.jpa.ips.api.IpsSectionContext;
import ca.uhn.fhir.jpa.ips.jpa.JpaSectionSearchStrategy;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.ResourceType;

public class PlanOfCareJpaSectionSearchStrategy extends JpaSectionSearchStrategy {

	@Override
	public void massageResourceSearch(
			IpsSectionContext theIpsSectionContext, SearchParameterMap theSearchParameterMap) {
		if (theIpsSectionContext.getResourceType().equals(ResourceType.CarePlan.name())) {
			theSearchParameterMap.add(
					CarePlan.SP_STATUS,
					new TokenOrListParam()
							.addOr(new TokenParam(
									CarePlan.CarePlanStatus.ACTIVE.getSystem(),
									CarePlan.CarePlanStatus.ACTIVE.toCode()))
							.addOr(new TokenParam(
									CarePlan.CarePlanStatus.ONHOLD.getSystem(),
									CarePlan.CarePlanStatus.ONHOLD.toCode()))
							.addOr(new TokenParam(
									CarePlan.CarePlanStatus.UNKNOWN.getSystem(),
									CarePlan.CarePlanStatus.UNKNOWN.toCode())));
		}
	}
}
