package ca.uhn.fhir.fluentpath;

/*
 * #%L
 * HAPI FHIR - Core Library
 * %%
 * Copyright (C) 2014 - 2019 University Health Network
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

import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.List;
import java.util.Optional;

public interface IFluentPath {

	/**
	 * Apply the given FluentPath expression against the given input and return
	 * all results in a list
	 * 
	 * @param theInput The input object (generally a resource or datatype)
	 * @param thePath The fluent path expression
	 * @param theReturnType The type to return (in order to avoid casting)
	 */
	<T extends IBase> List<T> evaluate(IBase theInput, String thePath, Class<T> theReturnType);

	/**
	 * Apply the given FluentPath expression against the given input and return
	 * the first match (if any)
	 *
	 * @param theInput The input object (generally a resource or datatype)
	 * @param thePath The fluent path expression
	 * @param theReturnType The type to return (in order to avoid casting)
	 */
	<T extends IBase> Optional<T> evaluateFirst(IBase theInput, String thePath, Class<T> theReturnType);


	IExpressionNode parse(String path);
	IExpressionNodeWithOffset parsePartial(String path, int offset);

	String evaluateToString(Object theAppInfo, IBaseResource theResource, IBase theBase, IExpressionNode theCompiled);
	boolean evaluateToBoolean(Object theAppInfo, IBaseResource theResource, IBase theBase, IExpressionNode theCompiled);
	List<IBase> evaluate(Object theAppInfo, IBaseResource theResource, IBase theBase, IExpressionNode theCompiled);

	void setHostServices(INarrativeConstantResolver theNarrativeConstantEvaluator);
	INarrativeConstantMap createLiquidIncludeMap();
	void setEnvironmentVariable(String key, String value);
}
