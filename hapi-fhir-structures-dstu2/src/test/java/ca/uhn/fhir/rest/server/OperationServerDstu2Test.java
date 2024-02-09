package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.dstu2.composite.MoneyDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Conformance;
import ca.uhn.fhir.model.dstu2.resource.Conformance.RestOperation;
import ca.uhn.fhir.model.dstu2.resource.OperationDefinition;
import ca.uhn.fhir.model.dstu2.resource.Parameters;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.OperationParameterUseEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.IntegerDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.primitive.UnsignedIntDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.TestUtil;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.not;

public class OperationServerDstu2Test {
	private static final FhirContext ourCtx = FhirContext.forDstu2Cached();

	private static IdDt ourLastId;
	private static String ourLastMethod;
	private static StringDt ourLastParam1;
	private static Patient ourLastParam2;
	private static List<StringDt> ourLastParam3;
	private static MoneyDt ourLastParamMoney1;
	private static UnsignedIntDt ourLastParamUnsignedInt1;
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(OperationServerDstu2Test.class);

	@RegisterExtension
	public static final RestfulServerExtension ourServer  = new RestfulServerExtension(ourCtx)
		.setDefaultResponseEncoding(EncodingEnum.XML)
		.registerProvider(new PatientProvider())
		.registerProvider(new PlainProvider())
		.withPagingProvider(new FifoMemoryPagingProvider(10).setDefaultPageSize(2))
		.setDefaultPrettyPrint(false);

	@RegisterExtension
	public static final HttpClientExtension ourClient = new HttpClientExtension();

	@BeforeEach
	public void before() {
		ourLastParam1 = null;
		ourLastParam2 = null;
		ourLastParam3 = null;
		ourLastParamUnsignedInt1 = null;
		ourLastParamMoney1 = null;
		ourLastId = null;
		ourLastMethod = "";
	}

	@Test
	public void testConformance() throws Exception {
		Conformance p = ourServer.getFhirClient().fetchConformance().ofType(Conformance.class).prettyPrint().execute();
		List<RestOperation> ops = p.getRest().get(0).getOperation();
		assertThat(ops.size()).isGreaterThan(1);
		assertThat(ops.get(0).getDefinition().getReference().getBaseUrl()).isNull();
		assertThat(ops.get(0).getDefinition().getReference().getValue()).startsWith("OperationDefinition/");

		OperationDefinition def = ourServer.getFhirClient().read().resource(OperationDefinition.class).withId(ops.get(0).getDefinition().getReference()).execute();
		assertThat(def.getCode(), not(blankOrNullString()));

		List<String> opNames = toOpNames(ops);
		assertThat(opNames, containsInRelativeOrder("OP_TYPE"));

		assertThat(ops.get(opNames.indexOf("OP_TYPE")).getDefinition().getReference().getValue()).isEqualTo("OperationDefinition/Patient-t-OP_TYPE");
	}

	/**
	 * See #380
	 */
	@Test
	public void testOperationDefinition() {
		OperationDefinition def = ourServer.getFhirClient().read().resource(OperationDefinition.class).withId("OperationDefinition/Patient-t-OP_TYPE").execute();
		
		ourLog.debug(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(def));

//		@OperationParam(name="PARAM1") StringType theParam1,
//		@OperationParam(name="PARAM2") Patient theParam2,
//		@OperationParam(name="PARAM3", min=2, max=5) List<StringType> theParam3,
//		@OperationParam(name="PARAM4", min=1) List<StringType> theParam4,

		assertThat(def.getParameter()).hasSize(4);
		assertThat(def.getParameter().get(0).getName()).isEqualTo("PARAM1");
		assertThat(def.getParameter().get(0).getUse()).isEqualTo(OperationParameterUseEnum.IN.getCode());
		assertThat(def.getParameter().get(0).getMin().intValue()).isEqualTo(0);
		assertThat(def.getParameter().get(0).getMax()).isEqualTo("1");

		assertThat(def.getParameter().get(1).getName()).isEqualTo("PARAM2");
		assertThat(def.getParameter().get(1).getUse()).isEqualTo(OperationParameterUseEnum.IN.getCode());
		assertThat(def.getParameter().get(1).getMin().intValue()).isEqualTo(0);
		assertThat(def.getParameter().get(1).getMax()).isEqualTo("1");

		assertThat(def.getParameter().get(2).getName()).isEqualTo("PARAM3");
		assertThat(def.getParameter().get(2).getUse()).isEqualTo(OperationParameterUseEnum.IN.getCode());
		assertThat(def.getParameter().get(2).getMin().intValue()).isEqualTo(2);
		assertThat(def.getParameter().get(2).getMax()).isEqualTo("5");

		assertThat(def.getParameter().get(3).getName()).isEqualTo("PARAM4");
		assertThat(def.getParameter().get(3).getUse()).isEqualTo(OperationParameterUseEnum.IN.getCode());
		assertThat(def.getParameter().get(3).getMin().intValue()).isEqualTo(1);
		assertThat(def.getParameter().get(3).getMax()).isEqualTo("*");
		
	}

	
	private List<String> toOpNames(List<RestOperation> theOps) {
		ArrayList<String> retVal = new ArrayList<String>();
		for (RestOperation next : theOps) {
			retVal.add(next.getName());
		}
		return retVal;
	}

	@Test
	public void testInstanceEverythingGet() throws Exception {

		// Try with a GET
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/123/$everything");
		CloseableHttpResponse status = ourClient.execute(httpGet);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("instance $everything");
		assertThat(response).startsWith("<Bundle");
		assertThat(ourLastId.toUnqualifiedVersionless().getValue()).isEqualTo("Patient/123");

	}

	@Test
	public void testInstanceEverythingHapiClient() throws Exception {
		Parameters p = ourCtx.newRestfulGenericClient(ourServer.getBaseUrl()).operation().onInstance(new IdDt("Patient/123")).named("$everything").withParameters(new Parameters()).execute();
		Bundle b = (Bundle) p.getParameterFirstRep().getResource();

		assertThat(ourLastMethod).isEqualTo("instance $everything");
		assertThat(ourLastId.toUnqualifiedVersionless().getValue()).isEqualTo("Patient/123");

	}

	@Test
	public void testInstanceEverythingPost() throws Exception {
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(new Parameters());

		// Try with a POST
		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/123/$everything");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("instance $everything");
		assertThat(response).startsWith("<Bundle");
		assertThat(ourLastId.toUnqualifiedVersionless().getValue()).isEqualTo("Patient/123");

	}

	@Test
	public void testOperationCantUseGetIfItIsntIdempotent() throws Exception {
		HttpGet httpPost = new HttpGet(ourServer.getBaseUrl() + "/Patient/123/$OP_INSTANCE");
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(Constants.STATUS_HTTP_405_METHOD_NOT_ALLOWED);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(status.getFirstHeader(Constants.HEADER_ALLOW).getValue()).isEqualTo("POST");
		assertThat(response).contains("HTTP Method GET is not allowed");
	}

	@Test
	public void testOperationWrongParameterType() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new IntegerDt(123));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/123/$OP_INSTANCE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		CloseableHttpResponse status = ourClient.execute(httpPost);
		try {
			String response = IOUtils.toString(status.getEntity().getContent());
			assertThat(response).contains("Request has parameter PARAM1 of type IntegerDt but method expects type StringDt");
			ourLog.info(response);
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testOperationOnInstance() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/123/$OP_INSTANCE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastId.getIdPart()).isEqualTo("123");
		assertThat(ourLastMethod).isEqualTo("$OP_INSTANCE");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");

		/*
		 * Against type should fail
		 */

		httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_INSTANCE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		status = ourClient.execute(httpPost);

		response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(response);
		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(400);

	}

	@Test
	public void testOperationOnInstanceAndType_Instance() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/123/$OP_INSTANCE_OR_TYPE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastId.getIdPart()).isEqualTo("123");
		assertThat(ourLastMethod).isEqualTo("$OP_INSTANCE_OR_TYPE");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");

	}

	@Test
	public void testOperationOnInstanceAndType_Type() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_INSTANCE_OR_TYPE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		CloseableHttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastId).isNull();
		assertThat(ourLastMethod).isEqualTo("$OP_INSTANCE_OR_TYPE");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");
	}

	@Test
	public void testOperationOnServer() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/$OP_SERVER");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastMethod).isEqualTo("$OP_SERVER");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");
	}

	@Test
	public void testOperationOnType() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_TYPE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastMethod).isEqualTo("$OP_TYPE");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");
	}

	@Test
	public void testOperationOnTypeReturnBundle() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new StringDt("PARAM1val"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_TYPE_RET_BUNDLE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastMethod).isEqualTo("$OP_TYPE_RET_BUNDLE");

		Bundle resp = ourCtx.newXmlParser().parseResource(Bundle.class, response);
		assertThat(resp.getEntryFirstRep().getResponse().getStatus()).isEqualTo("100");
	}

	@Test
	public void testOperationWithBundleProviderResponse() throws Exception {
		HttpGet httpPost = new HttpGet(ourServer.getBaseUrl() + "/$OP_INSTANCE_BUNDLE_PROVIDER?_pretty=true");
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());
		ourLog.info(response);

		Bundle resp = ourCtx.newXmlParser().parseResource(Bundle.class, response);
	}

	@Test
	public void testOperationWithGetUsingParams() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/$OP_TYPE?PARAM1=PARAM1val");
		HttpResponse status = ourClient.execute(httpGet);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastParam1.getValue()).isEqualTo("PARAM1val");
		assertThat(ourLastParam2).isNull();
		assertThat(ourLastMethod).isEqualTo("$OP_TYPE");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");
	}

	@Test
	public void testOperationWithGetUsingParamsFailsWithNonPrimitive() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/$OP_TYPE?PARAM1=PARAM1val&PARAM2=foo");
		HttpResponse status = ourClient.execute(httpGet);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(405);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(status.getFirstHeader(Constants.HEADER_ALLOW).getValue()).isEqualTo("POST");
		assertThat(response).contains("Can not invoke operation $OP_TYPE using HTTP GET because parameter PARAM2 is not a primitive datatype");
	}

	@Test
	public void testOperationWithListParam() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		p.addParameter().setName("PARAM3").setValue(new StringDt("PARAM3val1"));
		p.addParameter().setName("PARAM3").setValue(new StringDt("PARAM3val2"));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/$OP_SERVER_LIST_PARAM");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("$OP_SERVER_LIST_PARAM");
		assertThat(ourLastParam2.getActive().booleanValue()).isEqualTo(true);
		assertThat(ourLastParam1).isNull();
		assertThat(ourLastParam3).hasSize(2);
		assertThat(ourLastParam3.get(0).getValue()).isEqualTo("PARAM3val1");
		assertThat(ourLastParam3.get(1).getValue()).isEqualTo("PARAM3val2");

		Parameters resp = ourCtx.newXmlParser().parseResource(Parameters.class, response);
		assertThat(resp.getParameter().get(0).getName()).isEqualTo("RET1");
	}

	@Test
	public void testOperationWithProfileDatatypeParams() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new IntegerDt("123"));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_PROFILE_DT");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("$OP_PROFILE_DT");
		assertThat(ourLastParamUnsignedInt1.getValueAsString()).isEqualTo("123");
	}

	@Test
	public void testOperationWithProfileDatatypeParams2() throws Exception {
		Parameters p = new Parameters();
		MoneyDt money = new MoneyDt();
		money.setCode("CODE");
		money.setSystem("SYSTEM");
		money.setValue(123L);
		p.addParameter().setName("PARAM1").setValue(money);
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_PROFILE_DT2");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("$OP_PROFILE_DT2");
		assertThat(ourLastParamMoney1.getCode()).isEqualTo("CODE");
		assertThat(ourLastParamMoney1.getSystem()).isEqualTo("SYSTEM");
		assertThat(ourLastParamMoney1.getValue().toString()).isEqualTo("123");
	}

	@Test
	public void testOperationWithProfileDatatypeUrl() throws Exception {
		HttpGet httpPost = new HttpGet(ourServer.getBaseUrl() + "/Patient/$OP_PROFILE_DT?PARAM1=123");
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("$OP_PROFILE_DT");
		assertThat(ourLastParamUnsignedInt1.getValueAsString()).isEqualTo("123");
	}

	@Test
	public void testOperationWrongParamType() throws Exception {
		Parameters p = new Parameters();
		p.addParameter().setName("PARAM1").setValue(new IntegerDt("123"));
		p.addParameter().setName("PARAM2").setResource(new Patient().setActive(true));
		String inParamsStr = ourCtx.newXmlParser().encodeResourceToString(p);

		HttpPost httpPost = new HttpPost(ourServer.getBaseUrl() + "/Patient/$OP_TYPE");
		httpPost.setEntity(new StringEntity(inParamsStr, ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));
		HttpResponse status = ourClient.execute(httpPost);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(400);
		String response = IOUtils.toString(status.getEntity().getContent());
		IOUtils.closeQuietly(status.getEntity().getContent());

		ourLog.info(status.getStatusLine().toString());
		ourLog.info(response);

		assertThat(response).contains("Request has parameter PARAM1 of type IntegerDt but method expects type StringDt");
	}

	@Test
	public void testReadWithOperations() throws Exception {
		HttpGet httpGet = new HttpGet(ourServer.getBaseUrl() + "/Patient/123");
		HttpResponse status = ourClient.execute(httpGet);

		assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
		IOUtils.closeQuietly(status.getEntity().getContent());

		assertThat(ourLastMethod).isEqualTo("read");
	}

	@AfterAll
	public static void afterClassClearContext() throws Exception {
		TestUtil.randomizeLocaleAndTimezone();
	}

	public static class PatientProvider implements IResourceProvider {

		@Override
		public Class<? extends IResource> getResourceType() {
			return Patient.class;
		}

		//@formatter:off
		@Operation(name="$OP_INSTANCE")
		public Parameters opInstance(
				@IdParam IdDt theId,
				@OperationParam(name="PARAM1") StringDt theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				) {
			//@formatter:on

			ourLastMethod = "$OP_INSTANCE";
			ourLastId = theId;
			ourLastParam1 = theParam1;
			ourLastParam2 = theParam2;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_INSTANCE_OR_TYPE")
		public Parameters opInstanceOrType(
				@IdParam(optional=true) IdDt theId,
				@OperationParam(name="PARAM1") StringDt theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				) {
			//@formatter:on

			ourLastMethod = "$OP_INSTANCE_OR_TYPE";
			ourLastId = theId;
			ourLastParam1 = theParam1;
			ourLastParam2 = theParam2;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_PROFILE_DT2", idempotent=true)
		public Bundle opProfileDt(
				@OperationParam(name="PARAM1") MoneyDt theParam1
				) {
			//@formatter:on

			ourLastMethod = "$OP_PROFILE_DT2";
			ourLastParamMoney1 = theParam1;

			Bundle retVal = new Bundle();
			retVal.addEntry().getResponse().setStatus("100");
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_PROFILE_DT", idempotent=true)
		public Bundle opProfileDt(
				@OperationParam(name="PARAM1") UnsignedIntDt theParam1
				) {
			//@formatter:on

			ourLastMethod = "$OP_PROFILE_DT";
			ourLastParamUnsignedInt1 = theParam1;

			Bundle retVal = new Bundle();
			retVal.addEntry().getResponse().setStatus("100");
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_TYPE", idempotent=true)
		public Parameters opType(
				@OperationParam(name="PARAM1") StringDt theParam1,
				@OperationParam(name="PARAM2") Patient theParam2,
				@OperationParam(name="PARAM3", min=2, max=5) List<StringDt> theParam3,
				@OperationParam(name="PARAM4", min=1) List<StringDt> theParam4
				) {
			//@formatter:on

			ourLastMethod = "$OP_TYPE";
			ourLastParam1 = theParam1;
			ourLastParam2 = theParam2;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_TYPE_ONLY_STRING", idempotent=true)
		public Parameters opTypeOnlyString(
				@OperationParam(name="PARAM1") StringDt theParam1
				) {
			//@formatter:on

			ourLastMethod = "$OP_TYPE";
			ourLastParam1 = theParam1;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_TYPE_RET_BUNDLE")
		public Bundle opTypeRetBundle(
				@OperationParam(name="PARAM1") StringDt theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				) {
			//@formatter:on

			ourLastMethod = "$OP_TYPE_RET_BUNDLE";
			ourLastParam1 = theParam1;
			ourLastParam2 = theParam2;

			Bundle retVal = new Bundle();
			retVal.addEntry().getResponse().setStatus("100");
			return retVal;
		}

		@Operation(name = "$everything", idempotent = true)
		public Bundle patientEverything(@IdParam IdDt thePatientId) {
			ourLastMethod = "instance $everything";
			ourLastId = thePatientId;
			return new Bundle();
		}

		/**
		 * Just to make sure this method doesn't "steal" calls
		 */
		@Read
		public Patient read(@IdParam IdDt theId) {
			ourLastMethod = "read";
			Patient retVal = new Patient();
			retVal.setId(theId);
			return retVal;
		}

	}

	public static class PlainProvider {

		//@formatter:off
		@Operation(name="$OP_INSTANCE_BUNDLE_PROVIDER", idempotent=true)
		public IBundleProvider opInstanceReturnsBundleProvider() {
			ourLastMethod = "$OP_INSTANCE_BUNDLE_PROVIDER";

			List<IBaseResource> resources = new ArrayList<>();
			for (int i =0; i < 100;i++) {
				Patient p = new Patient();
				p.setId("Patient/" + i);
				p.addName().addFamily("Patient " + i);
				resources.add(p);
			}
			
			return new SimpleBundleProvider(resources);
		}

		//@formatter:off
		@Operation(name="$OP_SERVER")
		public Parameters opServer(
				@OperationParam(name="PARAM1") StringDt theParam1,
				@OperationParam(name="PARAM2") Patient theParam2
				) {
			//@formatter:on

			ourLastMethod = "$OP_SERVER";
			ourLastParam1 = theParam1;
			ourLastParam2 = theParam2;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

		//@formatter:off
		@Operation(name="$OP_SERVER_LIST_PARAM")
		public Parameters opServerListParam(
				@OperationParam(name="PARAM2") Patient theParam2,
				@OperationParam(name="PARAM3") List<StringDt> theParam3
				) {
			//@formatter:on

			ourLastMethod = "$OP_SERVER_LIST_PARAM";
			ourLastParam2 = theParam2;
			ourLastParam3 = theParam3;

			Parameters retVal = new Parameters();
			retVal.addParameter().setName("RET1").setValue(new StringDt("RETVAL1"));
			return retVal;
		}

	}

}
