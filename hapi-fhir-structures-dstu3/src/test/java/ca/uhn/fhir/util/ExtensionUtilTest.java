package ca.uhn.fhir.util;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionUtilTest {

	@Test
	public void testExtractChildPrimitiveExtensionValue() {

		Patient p = new Patient();
		Extension parent = p.addExtension().setUrl("parent");
		parent.addExtension("child1", new BooleanType(true));
		parent.addExtension("child2", new BooleanType(false));
		parent.addExtension("child3", new Quantity(123));

		assertThat(ExtensionUtil.extractChildPrimitiveExtensionValue(parent, "child2")).isEqualTo("false");
		assertThat(ExtensionUtil.extractChildPrimitiveExtensionValue(parent, "unknown")).isNull();
		assertThat(ExtensionUtil.extractChildPrimitiveExtensionValue(parent, "child3")).isNull();

	}


}
