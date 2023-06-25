package ca.uhn.fhir.jpa.subscription.websocket;

import ca.uhn.fhir.jpa.provider.BaseResourceProviderDstu2Test;
import ca.uhn.fhir.jpa.subscription.FhirDstu2Util;
import ca.uhn.fhir.jpa.test.util.SubscriptionTestUtil;
import ca.uhn.fhir.jpa.util.WebsocketSubscriptionClient;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.resource.Subscription;
import ca.uhn.fhir.model.dstu2.resource.Subscription.Channel;
import ca.uhn.fhir.model.dstu2.valueset.ObservationStatusEnum;
import ca.uhn.fhir.model.dstu2.valueset.SubscriptionChannelTypeEnum;
import ca.uhn.fhir.model.dstu2.valueset.SubscriptionStatusEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import static org.hamcrest.Matchers.contains;

/**
 * Adds a FHIR subscription with criteria through the rest interface. Then creates a websocket with
 * the id of the subscription
 *
 * <p>Note: This test only returns a ping with the subscription id, Check
 * FhirSubscriptionWithSubscriptionIdDstu3Test for a test that returns the xml of the observation
 *
 * <p>To execute the following test, execute it the following way: 0. execute 'clean' test 1.
 * Execute the 'createSubscription' test 2. Update the subscription id in the 'attachWebSocket' test
 * 3. Execute the 'attachWebSocket' test 4. Execute the 'sendObservation' test 5. Look in the
 * 'attachWebSocket' terminal execution and wait for your ping with the subscription id
 */
public class WebsocketWithSubscriptionIdDstu2Test extends BaseResourceProviderDstu2Test {
	private static final Logger ourLog =
				org.slf4j.LoggerFactory.getLogger(WebsocketWithSubscriptionIdDstu2Test.class);

	@RegisterExtension
	private final WebsocketSubscriptionClient myWebsocketClientExtension =
				new WebsocketSubscriptionClient(() -> myServer, () -> myStorageSettings);

	private String myPatientId;
	private String mySubscriptionId;
	@Autowired private SubscriptionTestUtil mySubscriptionTestUtil;

	@Override
	@AfterEach
	public void after() throws Exception {
		super.after();
		mySubscriptionTestUtil.unregisterSubscriptionInterceptor();
	}

	@Override
	@BeforeEach
	public void before() throws Exception {
		super.before();

		mySubscriptionTestUtil.registerWebSocketInterceptor();

		/*
			* Create patient
			*/

		Patient patient = FhirDstu2Util.getPatient();
		MethodOutcome methodOutcome = myClient.create().resource(patient).execute();
		myPatientId = methodOutcome.getId().getIdPart();

		/*
			* Create subscription
			*/
		Subscription subscription = new Subscription();
		subscription.setReason(
					"Monitor new neonatal function (note, age will be determined by the monitor)");
		subscription.setStatus(SubscriptionStatusEnum.ACTIVE);
		// subscription.setCriteria("Observation?subject=Patient/" + PATIENT_ID);
		subscription.setCriteria("Observation?code=SNOMED-CT|82313006");

		Channel channel = new Channel();
		channel.setType(SubscriptionChannelTypeEnum.WEBSOCKET);
		channel.setPayload("application/json");
		subscription.setChannel(channel);

		methodOutcome = myClient.create().resource(subscription).execute();
		mySubscriptionId = methodOutcome.getId().getIdPart();

		myWebsocketClientExtension.bind(mySubscriptionId);
	}

	@Test
	public void createObservation() {
		Observation observation = new Observation();
		CodeableConceptDt cc = new CodeableConceptDt();
		observation.setCode(cc);
		CodingDt coding = cc.addCoding();
		coding.setCode("82313006");
		coding.setSystem("SNOMED-CT");
		ResourceReferenceDt reference = new ResourceReferenceDt();
		reference.setReference("Patient/" + myPatientId);
		observation.setSubject(reference);
		observation.setStatus(ObservationStatusEnum.FINAL);

		MethodOutcome methodOutcome2 = myClient.create().resource(observation).execute();
		String observationId = methodOutcome2.getId().getIdPart();
		observation.setId(observationId);

		ourLog.info("Observation id generated by server is: " + observationId);

		ourLog.info("WS Messages: {}", myWebsocketClientExtension.getMessages());
		waitForSize(2, myWebsocketClientExtension.getMessages());
		MatcherAssert.assertThat(
					myWebsocketClientExtension.getMessages(),
					contains("bound " + mySubscriptionId, "ping " + mySubscriptionId));
	}

	@Test
	public void createObservationThatDoesNotMatch() {
		Observation observation = new Observation();
		CodeableConceptDt cc = new CodeableConceptDt();
		observation.setCode(cc);
		CodingDt coding = cc.addCoding();
		coding.setCode("8231");
		coding.setSystem("SNOMED-CT");
		ResourceReferenceDt reference = new ResourceReferenceDt();
		reference.setReference("Patient/" + myPatientId);
		observation.setSubject(reference);
		observation.setStatus(ObservationStatusEnum.FINAL);

		MethodOutcome methodOutcome2 = myClient.create().resource(observation).execute();
		String observationId = methodOutcome2.getId().getIdPart();
		observation.setId(observationId);

		ourLog.info("Observation id generated by server is: " + observationId);

		ourLog.info("WS Messages: {}", myWebsocketClientExtension.getMessages());
		waitForSize(1, myWebsocketClientExtension.getMessages());
		MatcherAssert.assertThat(
					myWebsocketClientExtension.getMessages(), contains("bound " + mySubscriptionId));
	}
}
