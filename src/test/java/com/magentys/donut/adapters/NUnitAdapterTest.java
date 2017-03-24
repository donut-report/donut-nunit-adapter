package com.magentys.donut.adapters;

import com.magentys.donut.gherkin.model.Element;
import com.magentys.donut.gherkin.model.Feature;
import com.magentys.donut.gherkin.model.Step;
import com.sun.org.apache.xerces.internal.dom.DeferredElementImpl;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class NUnitAdapterTest {

    private NUnitAdapter nUnitAdapter;
    private final String sample1Path = FileUtils.toFile(NUnitAdapterTest.class.getResource("/nunit/sample-1/TestResult.xml")).getAbsolutePath();
    private final String sample2Path = FileUtils.toFile(NUnitAdapterTest.class.getResource("/nunit/sample-2/TestResult.xml")).getAbsolutePath();
    private final String sample3Path = FileUtils.toFile(NUnitAdapterTest.class.getResource("/nunit/sample-3/TestResult.xml")).getAbsolutePath();
    private final String sample4Path = FileUtils.toFile(NUnitAdapterTest.class.getResource("/nunit/sample-4/TestResult.xml")).getAbsolutePath();
    private final String sample5Path = FileUtils.toFile(NUnitAdapterTest.class.getResource("/nunit/sample-5/TestResult.xml")).getAbsolutePath();

    @Before
    public void setUp() {
        nUnitAdapter = new NUnitAdapter();
    }

    // Behavior
    @Test
    public void shouldBeAbleToTransformTheDocumentToFeaturesList() throws Exception {
        Document document = nUnitAdapter.extractDocument(sample1Path);
        List<Feature> features = nUnitAdapter.transform(document);

        assertFalse(features.isEmpty());
    }

    @Test
    public void shouldBeAbleToTransformTheDocumentWithMultipleTestFixturesAtTheSameLevelToFeaturesList() throws Exception {
        Document document = nUnitAdapter.extractDocument(sample3Path);
        List<Feature> features = nUnitAdapter.transform(document);

        assertTrue(features.size() == 2);
    }

    @Test
    public void shouldBeAbleToTransformTheDocumentWithMultipleTestFixturesAtDifferentLevelToFeaturesList() throws Exception {
        Document document = nUnitAdapter.extractDocument(sample4Path);
        List<Feature> features = nUnitAdapter.transform(document);

        System.out.println(features.size());
        assertTrue(features.size() == 5);
    }

    @Test
    public void shouldBeAbleToTransformResultXmlWithFailure() throws Exception {
        Document document = nUnitAdapter.extractDocument(sample2Path);
        List<Feature> features = nUnitAdapter.transform(document);
        List<Element> scenarios = features.get(0).getElements();
        Element scenario = scenarios.get(0);
        Step step = scenario.getSteps().get(0);

        assertTrue(features.size() == 1);
        assertTrue(scenarios.size() == 2);
        assertTrue(scenario.getName().equals("Comparison"));
        assertTrue(step.getResult().getErrorMessage().contains("Error message:"));
        assertTrue(step.getResult().getStatus().equals("failed"));
    }

    // Units
    @Test
    public void shouldBeAbleToReadTheXmlFileUsingAnAbsolutePath() throws IOException {
        File xmlFile = nUnitAdapter.readXml(sample1Path);

        assertTrue(xmlFile != null);
        assertTrue(xmlFile.getName().equals("TestResult.xml"));
    }

    @Test
    public void shouldBeAbleToTransformTheReadXmlFileToDocument() throws IOException, SAXException, ParserConfigurationException {
        Document document = nUnitAdapter.extractDocument(sample1Path);

        assertTrue(document != null);
        assertTrue(document.getDocumentElement().getTagName().equals("test-run"));
    }

    @Test
    public void shouldExtractTestFixturesThatAreUnderSameTestSuite() throws Exception {
        List<Node> testFixtures = nUnitAdapter.extractTestFixtures(nUnitAdapter.extractDocument(sample3Path));

        System.out.println(testFixtures.size());
        assertTrue(testFixtures.size() == 2);
    }

    @Test
    public void shouldExtractTestFixturesThatAreUnderSameAssemblyButDifferentTestSuite() throws Exception {
        List<Node> testFixtures = nUnitAdapter.extractTestFixtures(nUnitAdapter.extractDocument(sample4Path));

        System.out.println(testFixtures.size());
        assertTrue(testFixtures.size() == 5);
    }

    @Test
    public void shouldExtractTestFixturesThatAreUnderDifferentAssemblies() throws Exception {
        List<Node> testFixtures = nUnitAdapter.extractTestFixtures(nUnitAdapter.extractDocument(sample5Path));

        System.out.println(testFixtures.size());
        assertTrue(testFixtures.size() == 2);
    }

    @Test
    public void shouldMakeElementsWithNameAttributeAsDefaultWhenNamePropertyIsNotSpecified() throws Exception {

        //Assumption: This testCase doesn't have properties
        Node testCase = buildTestCase();
        String name = ((DeferredElementImpl) testCase).getAttribute("name");
        assertNotNull(name);

        List<Node> testCaseNodes = Collections.singletonList(testCase);
        List<Element> elements = nUnitAdapter.makeElements(testCaseNodes);

        assert (elements.size() == 1);
        Element element = elements.get(0);
        assert (element.getName().equals(name));
    }

    @Test
    public void shouldMakeElementsWithNamePropertyWhenNamePropertyIsSpecified() throws Exception {

        Node testCase = buildTestCase();
        String nameValue = "User is hungry, so he should be offered food";
        Map<String, String> properties = new HashMap<String, String>() {
            {
                put("name", "Name");
                put("value", nameValue);
            }
        };

        appendPropertiesNode(testCase, Collections.singletonList(properties));

        List<Node> testCaseNodes = Collections.singletonList(testCase);
        List<Element> elements = nUnitAdapter.makeElements(testCaseNodes);

        assert (elements.size() == 1);
        Element element = elements.get(0);
        assert (element.getName().equals(nameValue));
    }

    private void appendPropertiesNode(Node testCase, List<Map<String, String>> properties) {
        Document document = testCase.getOwnerDocument();
        Node propertiesNode = document.createElement("properties");
        testCase.appendChild(propertiesNode);

        org.w3c.dom.Element propertyElement = document.createElement("property");

        for (Map<String, String> property : properties) {
            for (Map.Entry<String, String> propertyEntry : property.entrySet()) {
                propertyElement.setAttribute(propertyEntry.getKey(), propertyEntry.getValue());
            }
            propertiesNode.appendChild(propertyElement);
        }
    }

    /**
     * Returns the first test case node from a sample document kept at sample1Path. <br>This has the default structure i.e. it doesn't have properties etc.
     *
     * @return testCaseNode
     * @throws Exception Thrown if no test cases are found
     */
    private Node buildTestCase() throws Exception {
        Document document = nUnitAdapter.extractDocument(sample1Path);
        return nUnitAdapter.getNodes(document, "//test-case", "test-case").get(0);
    }
}
