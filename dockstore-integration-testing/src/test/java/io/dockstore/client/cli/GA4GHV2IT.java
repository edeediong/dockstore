package io.dockstore.client.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import io.dockstore.common.CommonTestUtilities;
import io.dockstore.common.IntegrationTest;
import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import io.swagger.client.model.MetadataV2;
import io.swagger.client.model.ToolClass;
import io.swagger.client.model.ToolDescriptor;
import io.swagger.client.model.ToolV2;
import io.swagger.client.model.ToolVersionV2;
import io.swagger.model.ToolFile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author gluu
 * @since 02/01/18
 */
@Category(IntegrationTest.class)
public class GA4GHV2IT extends GA4GHIT {
    private static final String apiVersion = "api/ga4gh/v2/";

    public String getApiVersion() {
        return apiVersion;
    }

    @Test
    public void metadata() throws Exception {
        Response response = checkedResponse(basePath + "metadata");
        MetadataV2 responseObject = response.readEntity(MetadataV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("api_version");
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("friendly_name");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("api-version");
        assertThat(MAPPER.writeValueAsString(responseObject)).doesNotContain("friendly-name");
    }

    @Test
    public void tools() throws Exception {
        Response response = checkedResponse(basePath + "tools");
        List<ToolV2> responseObject = response.readEntity(new GenericType<List<ToolV2>>() {
        });
        assertTool(MAPPER.writeValueAsString(responseObject), true);
    }

    @Test
    public void toolsId() throws Exception {
        toolsIdTool();
        toolsIdWorkflow();
    }

    private void toolsIdTool() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6");
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject), true);
    }

    private void toolsIdWorkflow() throws Exception {
        Response response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FA%2Fl");
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertTool(MAPPER.writeValueAsString(responseObject), false);
    }

    @Test
    public void toolsIdVersions() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions");
        List<ToolVersionV2> responseObject = response.readEntity(new GenericType<List<ToolVersionV2>>() {
        });
        assertVersion(MAPPER.writeValueAsString(responseObject));
    }

    @Test
    public void toolClasses() throws Exception {
        Response response = checkedResponse(basePath + "toolClasses");
        List<ToolClass> responseObject = response.readEntity(new GenericType<List<ToolClass>>() {
        });
        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/toolClasses.json"), new TypeReference<List<ToolClass>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    @Test
    public void toolsIdVersionsVersionId() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName");
        ToolVersionV2 responseObject = response.readEntity(ToolVersionV2.class);
        assertVersion(MAPPER.writeValueAsString(responseObject));
    }

    /**
     * This tests the /tools/{id}/versions/{version_id}/{type}/files endpoint
     *
     * @throws Exception
     */
    @Test
    public void toolsIdVersionsVersionIdTypeFile() throws Exception {
        toolsIdVersionsVersionIdTypeFileCWL();
        toolsIdVersionsVersionIdTypeFileWDL();
    }

    @Test
    public void toolsIdVersionsVersionIdTypeDescriptorRelativePathNoEncode() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/descriptor//Dockstore.cwl");
        ToolDescriptor responseObject = response.readEntity(ToolDescriptor.class);
        assertThat(response.getStatus()).isEqualTo(200);
        assertDescriptor(MAPPER.writeValueAsString(responseObject));
    }

    private void toolsIdVersionsVersionIdTypeFileCWL() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/CWL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<List<ToolFile>>() {
        });

        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/cwlFiles.json"), new TypeReference<List<ToolFile>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    private void toolsIdVersionsVersionIdTypeFileWDL() throws Exception {
        Response response = checkedResponse(basePath + "tools/quay.io%2Ftest_org%2Ftest6/versions/fakeName/WDL/files");
        List<ToolFile> responseObject = response.readEntity(new GenericType<List<ToolFile>>() {
        });
        final String expected = MAPPER
                .writeValueAsString(MAPPER.readValue(fixture("fixtures/wdlFiles.json"), new TypeReference<List<ToolFile>>() {
                }));
        assertThat(MAPPER.writeValueAsString(responseObject)).isEqualTo(expected);
    }

    protected void assertVersion(String version) {
        assertThat(version).contains("meta_version");
        assertThat(version).contains("descriptor_type");
        assertThat(version).contains("verified_source");
        assertThat(version).doesNotContain("meta-version");
        assertThat(version).doesNotContain("descriptor-type");
        assertThat(version).doesNotContain("verified-source");
    }

    protected void assertTool(String tool, boolean isTool) {
        assertThat(tool).contains("meta_version");
        assertThat(tool).contains("verified_source");
        assertThat(tool).doesNotContain("meta-version");
        assertThat(tool).doesNotContain("verified-source");
        if (isTool) {
            assertVersion(tool);
        }
    }

    /**
     * This tests if the 4 workflows with a combination of different repositories and either same or matching workflow name
     * can be retrieved separately.  In the test database, the author happens to uniquely identify the workflows.
     *
     * @throws Exception
     */
    @Test
    public void toolsIdGet4Workflows() throws Exception {
        // Insert the 4 workflows into the database using migrations
        CommonTestUtilities.setupSamePathsTest(SUPPORT);

        // Check responses
        Response response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository");
        ToolV2 responseObject = response.readEntity(ToolV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("author1");
        response = checkedResponse(basePath + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository");
        responseObject = response.readEntity(ToolV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("author2");
        response = checkedResponse(basePath + "tools/%23workflow%2Fgithub.com%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(ToolV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("author3");
        response = checkedResponse(basePath + "tools/%23workflow%2Fbitbucket.org%2FfakeOrganization%2FfakeRepository%2FPotato");
        responseObject = response.readEntity(ToolV2.class);
        assertThat(MAPPER.writeValueAsString(responseObject)).contains("author4");
    }

    /**
     * This tests cwl-runner with a workflow from GA4GH V2 relative-path endpoint (without encoding) that contains 2 more additional files
     * that will reference the GA4GH V2 endpoint
     * @throws Exception
     */
    @Test
    public void cwlrunnerWorkflowRelativePathNotEncodedAdditionalFiles() throws Exception {
        CommonTestUtilities.setupTestWorkflow(SUPPORT);
        String command = "cwl-runner";
        String descriptorPath = basePath + "tools/%23workflow%2Fgithub.com%2Fgaryluu%2FtestWorkflow/versions/master/plain-CWL/descriptor//Dockstore.cwl";
        String testParameterFilePath = ResourceHelpers.resourceFilePath("testWorkflow.json");
        ImmutablePair<String, String> stringStringImmutablePair = Utilities
                .executeCommand(command + " " + descriptorPath + " " + testParameterFilePath);
        Assert.assertTrue(stringStringImmutablePair.getRight().contains("Final process status is success"));
    }
}