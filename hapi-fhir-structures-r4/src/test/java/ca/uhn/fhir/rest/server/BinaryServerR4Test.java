package ca.uhn.fhir.rest.server;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.test.utilities.HttpClientExtension;
import ca.uhn.fhir.test.utilities.server.RestfulServerExtension;
import ca.uhn.fhir.util.TestUtil;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class 	BinaryServerR4Test {
	private static final FhirContext ourCtx = FhirContext.forR4Cached();
	private static Binary ourLastBinary;
	private static byte[] ourLastBinaryBytes;
	private static String ourLastBinaryString;
	private static IdType ourLastId;
	private static Binary ourNextBinary;

	@RegisterExtension
	public static final RestfulServerExtension ourServer = new RestfulServerExtension(ourCtx)
		 .registerProvider(new BinaryProvider())
		 .withPagingProvider(new FifoMemoryPagingProvider(100))
		 .setDefaultResponseEncoding(EncodingEnum.XML)
		 .setDefaultPrettyPrint(false);

	@RegisterExtension
	public static final HttpClientExtension ourClient = new HttpClientExtension();

	@BeforeEach
	public void before() {
		ourLastBinary = null;
		ourLastBinaryBytes = null;
		ourLastBinaryString = null;
		ourLastId = null;
		ourNextBinary = null;
	}

	@Test
	public void testGetWithNoAccept() throws Exception {

		ourNextBinary = new Binary();
		ourNextBinary.setId("Binary/A/_history/222");
		ourNextBinary.setContent(new byte[]{0, 1, 2, 3, 4});
		ourNextBinary.setSecurityContext(new Reference("Patient/1"));
		ourNextBinary.setContentType("application/foo");

		HttpGet get = new HttpGet(ourServer.getBaseUrl() + "/Binary/A");
		get.addHeader("Content-Type", "application/foo");
		CloseableHttpResponse status = ourClient.execute(get);
		try {
			assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
			assertThat(status.getEntity().getContentType().getValue()).isEqualTo("application/foo");
			assertThat(status.getFirstHeader(Constants.HEADER_X_SECURITY_CONTEXT).getValue()).isEqualTo("Patient/1");
			assertThat(status.getFirstHeader(Constants.HEADER_ETAG).getValue()).isEqualTo("W/\"222\"");
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_LOCATION).getValue()).isEqualTo(ourServer.getBaseUrl() + "/Binary/A/_history/222");
			assertThat(status.getFirstHeader(Constants.HEADER_LOCATION)).isNull();

			byte[] content = IOUtils.toByteArray(status.getEntity().getContent());
			assertThat(content).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}


	@Test
	public void testGetWithAccept() throws Exception {

		ourNextBinary = new Binary();
		ourNextBinary.setId("Binary/A/_history/222");
		ourNextBinary.setContent(new byte[]{0, 1, 2, 3, 4});
		ourNextBinary.setSecurityContext(new Reference("Patient/1"));
		ourNextBinary.setContentType("application/foo");

		HttpGet get = new HttpGet(ourServer.getBaseUrl() + "/Binary/A");
		get.addHeader("Content-Type", "application/foo");
		get.addHeader("Accept", Constants.CT_FHIR_JSON);
		CloseableHttpResponse status = ourClient.execute(get);
		try {
			assertThat(status.getStatusLine().getStatusCode()).isEqualTo(200);
			assertThat(status.getEntity().getContentType().getValue()).isEqualTo("application/json+fhir;charset=utf-8");
			assertThat(status.getFirstHeader(Constants.HEADER_X_SECURITY_CONTEXT).getValue()).isEqualTo("Patient/1");
			assertThat(status.getFirstHeader(Constants.HEADER_ETAG).getValue()).isEqualTo("W/\"222\"");
			assertThat(status.getFirstHeader(Constants.HEADER_CONTENT_LOCATION).getValue()).isEqualTo(ourServer.getBaseUrl() + "/Binary/A/_history/222");
			assertThat(status.getFirstHeader(Constants.HEADER_LOCATION)).isNull();

			String content = IOUtils.toString(status.getEntity().getContent(), Charsets.UTF_8);
			assertThat(content).isEqualTo("{\"resourceType\":\"Binary\",\"id\":\"A\",\"meta\":{\"versionId\":\"222\"},\"contentType\":\"application/foo\",\"securityContext\":{\"reference\":\"Patient/1\"},\"data\":\"AAECAwQ=\"}");
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testPostBinaryWithSecurityContext() throws Exception {
		HttpPost post = new HttpPost(ourServer.getBaseUrl() + "/Binary");
		post.setEntity(new ByteArrayEntity(new byte[]{0, 1, 2, 3, 4}));
		post.addHeader("Content-Type", "application/foo");
		post.addHeader(Constants.HEADER_X_SECURITY_CONTEXT, "Encounter/2");
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastId).isNull();
			assertThat(ourLastBinary.getContentType()).isEqualTo("application/foo");
			assertThat(ourLastBinary.getSecurityContext().getReference()).isEqualTo("Encounter/2");
			assertThat(ourLastBinary.getContent()).containsExactly(new byte[]{0, 1, 2, 3, 4});
			assertThat(ourLastBinaryBytes).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testPostRawBytesBinaryContentType() throws Exception {
		HttpPost post = new HttpPost(ourServer.getBaseUrl() + "/Binary");
		post.setEntity(new ByteArrayEntity(new byte[]{0, 1, 2, 3, 4}));
		post.addHeader("Content-Type", "application/foo");
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastId).isNull();
			assertThat(ourLastBinary.getContentType()).isEqualTo("application/foo");
			assertThat(ourLastBinary.getContent()).containsExactly(new byte[]{0, 1, 2, 3, 4});
			assertThat(ourLastBinaryBytes).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	/**
	 * Technically the client shouldn't be doing it this way, but we'll be accepting
	 */
	@Test
	public void testPostRawBytesFhirContentType() throws Exception {

		Binary b = new Binary();
		b.setContentType("application/foo");
		b.setContent(new byte[]{0, 1, 2, 3, 4});
		String encoded = ourCtx.newJsonParser().encodeResourceToString(b);

		HttpPost post = new HttpPost(ourServer.getBaseUrl() + "/Binary");
		post.setEntity(new StringEntity(encoded));
		post.addHeader("Content-Type", Constants.CT_FHIR_JSON);
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastBinary.getContentType()).isEqualTo("application/foo");
			assertThat(ourLastBinary.getContent()).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testPostRawBytesFhirContentTypeContainingFhir() throws Exception {

		Patient p = new Patient();
		p.getText().setDivAsString("A PATIENT");

		Binary b = new Binary();
		b.setContentType("application/xml+fhir");
		b.setContent(ourCtx.newXmlParser().encodeResourceToString(p).getBytes("UTF-8"));
		String encoded = ourCtx.newJsonParser().encodeResourceToString(b);

		HttpPost post = new HttpPost(ourServer.getBaseUrl() + "/Binary");
		post.setEntity(new StringEntity(encoded));
		post.addHeader("Content-Type", Constants.CT_FHIR_JSON);
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastBinary.getContentType()).isEqualTo("application/xml+fhir");
			assertThat(ourLastBinary.getContent()).containsExactly(b.getContent());
			assertThat(ourLastBinaryString).isEqualTo(encoded);
			assertThat(ourLastBinaryBytes).containsExactly(encoded.getBytes("UTF-8"));
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testPostRawBytesNoContentType() throws Exception {
		HttpPost post = new HttpPost(ourServer.getBaseUrl() + "/Binary");
		post.setEntity(new ByteArrayEntity(new byte[]{0, 1, 2, 3, 4}));
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastBinary.getContentType()).isNull();
			assertThat(ourLastBinary.getContent()).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@Test
	public void testPutBinaryWithSecurityContext() throws Exception {
		HttpPut post = new HttpPut(ourServer.getBaseUrl() + "/Binary/A");
		post.setEntity(new ByteArrayEntity(new byte[]{0, 1, 2, 3, 4}));
		post.addHeader("Content-Type", "application/foo");
		post.addHeader(Constants.HEADER_X_SECURITY_CONTEXT, "Encounter/2");
		CloseableHttpResponse status = ourClient.execute(post);
		try {
			assertThat(ourLastId.getValue()).isEqualTo("Binary/A");
			assertThat(ourLastBinary.getId()).isEqualTo("Binary/A");
			assertThat(ourLastBinary.getContentType()).isEqualTo("application/foo");
			assertThat(ourLastBinary.getSecurityContext().getReference()).isEqualTo("Encounter/2");
			assertThat(ourLastBinary.getContent()).containsExactly(new byte[]{0, 1, 2, 3, 4});
			assertThat(ourLastBinaryBytes).containsExactly(new byte[]{0, 1, 2, 3, 4});
		} finally {
			IOUtils.closeQuietly(status);
		}
	}

	@AfterAll
	public static void afterClassClearContext() throws Exception {
		TestUtil.randomizeLocaleAndTimezone();
	}

	public static class BinaryProvider implements IResourceProvider {
		@Create()
		public MethodOutcome createBinary(@ResourceParam Binary theBinary, @ResourceParam String theBinaryString, @ResourceParam byte[] theBinaryBytes) {
			ourLastBinary = theBinary;
			ourLastBinaryString = theBinaryString;
			ourLastBinaryBytes = theBinaryBytes;
			return new MethodOutcome(new IdType("Binary/001/_history/002"));
		}

		@Override
		public Class<? extends IBaseResource> getResourceType() {
			return Binary.class;
		}

		@Read
		public Binary read(@IdParam IdType theId) {
			return ourNextBinary;
		}

		@Update()
		public MethodOutcome updateBinary(@IdParam IdType theId, @ResourceParam Binary theBinary, @ResourceParam String theBinaryString, @ResourceParam byte[] theBinaryBytes) {
			ourLastId = theId;
			ourLastBinary = theBinary;
			ourLastBinaryString = theBinaryString;
			ourLastBinaryBytes = theBinaryBytes;
			return new MethodOutcome(new IdType("Binary/001/_history/002"));
		}

	}

}
