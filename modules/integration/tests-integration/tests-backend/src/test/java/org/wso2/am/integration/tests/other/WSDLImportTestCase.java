/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.wso2.am.integration.tests.other;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.publisher.api.ApiResponse;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyGenerateRequestDTO;
import org.wso2.am.integration.test.Constants;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.generic.TestConfigurationProvider;
import org.wso2.am.integration.tests.api.lifecycle.APIManagerLifecycleBaseTest;
import org.wso2.carbon.automation.engine.annotations.ExecutionEnvironment;
import org.wso2.carbon.automation.engine.annotations.SetEnvironment;
import org.wso2.carbon.automation.engine.frameworkutils.FrameworkPathUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;

/**
 * This test case is used to test the API creation with WSDL definitions
 */
@SetEnvironment(executionEnvironments = {ExecutionEnvironment.ALL})
public class WSDLImportTestCase extends APIManagerLifecycleBaseTest {
    private final Log log = LogFactory.getLog(WSDLImportTestCase.class);
    private String WSDL_FILE_API_NAME = "WSDLImportAPIWithWSDLFile";
    private String WSDL_FILE_API_CONTEXT = "wsdlimportwithwsdlfile";
    private String WSDL_ZIP_API_NAME = "WSDLImportAPIWithZipFile";
    private String WSDL_ZIP_API_CONTEXT = "wsdlimportwithzipfile";
    private String WSDL_URL_API_NAME = "WSDLImportAPIWithURL";
    private String WSDL_URL_API_CONTEXT = "wsdlimportwithurl";
    private String API_VERSION = "1.0.0";
    private String wsdlFileApiId;
    private String zipFileApiId;
    private String wsdlUrlApiId;
    private ArrayList<String> grantTypes;
    private String publisherURLHttps;
    private String userName;
    private String password;
    private String tenantDomain;
    private final String KEYSTORE_FILE_PATH_CLIENT =
            TestConfigurationProvider.getResourceLocation() + File.separator + "keystores" + File.separator + "products"
                    + File.separator + "wso2carbon.jks";
    private final String TRUSTSTORE_FILE_PATH_CLIENT =
            TestConfigurationProvider.getResourceLocation() + File.separator + "keystores" + File.separator + "products"
                    + File.separator + "client-truststore.jks";
    private String applicationId;
    private String accessToken;
    private String wsdlDefinition;
    private String requestBody;
    private String responseBody;
    private String endpointHost = "http://localhost";
    private int endpointPort;
    private int lowerPortLimit = 9950;
    private int upperPortLimit = 9999;
    private WireMockServer wireMockServer;
    private String apiEndPointURL;
    private String wsdlURL;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {
        log.info("WSDLImportTestCase initiated");

        super.init();
        tenantDomain = storeContext.getContextTenant().getDomain();
        userName = keyManagerContext.getContextTenant().getTenantAdmin().getUserName();
        password = keyManagerContext.getContextTenant().getTenantAdmin().getPassword();
        grantTypes = new ArrayList<>();
        publisherURLHttps = publisherUrls.getWebAppURLHttps();

        // Setting the system properties to call the etcd endpoint
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.ssl.keyStore", KEYSTORE_FILE_PATH_CLIENT);
        System.setProperty("javax.net.ssl.keyStorePassword", "wso2carbon");
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_FILE_PATH_CLIENT);
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");

        // Load request/response body
        wsdlDefinition = readFile(getAMResourceLocation() + File.separator + "soap" + File.separator
                + "phoneverify.wsdl");
        requestBody = readFile(getAMResourceLocation() + File.separator + "soap" + File.separator
                + "checkPhoneNumberRequestBody.xml");
        responseBody = readFile(getAMResourceLocation() + File.separator + "soap" + File.separator
                + "checkPhoneNumberResponseBody.xml");

        // Start wiremock server
        startWiremockServer();
        apiEndPointURL = endpointHost + ":" + endpointPort + "/phoneverify";
        wsdlURL = endpointHost + ":" + endpointPort + "/phoneverify/wsdl";
    }

    @Test(groups = {"wso2.am"}, description = "Importing WSDL API definition and create API")
    public void testWsdlDefinitionImport() throws Exception {
        log.info("testWsdlDefinitionImport initiated");

        // Set environment
        ArrayList<String> environment = new ArrayList<>();
        environment.add(Constants.GATEWAY_ENVIRONMENT);

        // Set policies
        ArrayList<String> policies = new ArrayList<>();
        policies.add(APIMIntegrationConstants.API_TIER.UNLIMITED);

        // Set endpointConfig
        JSONObject url = new JSONObject();
        url.put("url", apiEndPointURL);
        JSONObject endpointConfig = new JSONObject();
        endpointConfig.put("endpoint_type", "http");
        endpointConfig.put("sandbox_endpoints", url);
        endpointConfig.put("production_endpoints", url);

        // Create additional properties object
        JSONObject additionalPropertiesObj = new JSONObject();
        additionalPropertiesObj.put("provider", user.getUserName());
        additionalPropertiesObj.put("name", WSDL_FILE_API_NAME);
        additionalPropertiesObj.put("context", WSDL_FILE_API_CONTEXT);
        additionalPropertiesObj.put("version", API_VERSION);
        additionalPropertiesObj.put("policies", policies);
        additionalPropertiesObj.put("endpointConfig", endpointConfig);

        // Create API by importing the WSDL definition as .wsdl file
        String wsdlDefinitionPath = FrameworkPathUtil.getSystemResourceLocation() + "wsdl"
                + File.separator + "Sample.wsdl";
        File file = new File(wsdlDefinitionPath);
        APIDTO wsdlFileApidto = restAPIPublisher
                .importWSDLSchemaDefinition(file, null, additionalPropertiesObj.toString(), "SOAP");

        // Make sure API is created properly
        assertEquals(wsdlFileApidto.getName(), WSDL_FILE_API_NAME);
        assertEquals(wsdlFileApidto.getContext(), "/" + WSDL_FILE_API_CONTEXT);
        wsdlFileApiId = wsdlFileApidto.getId();

        // Create Revision and Deploy to Gateway
        createAPIRevisionAndDeployUsingRest(wsdlFileApiId, restAPIPublisher);
        waitForAPIDeploymentSync(userName, WSDL_FILE_API_NAME, API_VERSION, APIMIntegrationConstants.IS_API_EXISTS);
        restAPIPublisher.changeAPILifeCycleStatus(wsdlFileApiId, Constants.PUBLISHED);
        HttpResponse createdApiResponse1 = restAPIPublisher.getAPI(wsdlFileApiId);
        assertEquals(Response.Status.OK.getStatusCode(), createdApiResponse1.getResponseCode());

        // Update additional properties object
        additionalPropertiesObj.put("name", WSDL_ZIP_API_NAME);
        additionalPropertiesObj.put("context", WSDL_ZIP_API_CONTEXT);

        // Create API by importing the WSDL definition as .zip file (using archive)
        String wsdlZipDefinitionPath = FrameworkPathUtil.getSystemResourceLocation() + "wsdl"
                + File.separator + "Sample.zip";
        File zipFile = new File(wsdlZipDefinitionPath);
        APIDTO zipFileApidto = restAPIPublisher
                .importWSDLSchemaDefinition(zipFile, null, additionalPropertiesObj.toString(), "SOAP");

        // Make sure API is created properly
        assertEquals(zipFileApidto.getName(), WSDL_ZIP_API_NAME);
        assertEquals(zipFileApidto.getContext(), "/" + WSDL_ZIP_API_CONTEXT);
        zipFileApiId = zipFileApidto.getId();

        // Create Revision and Deploy to Gateway
        createAPIRevisionAndDeployUsingRest(zipFileApiId, restAPIPublisher);
        waitForAPIDeploymentSync(userName, WSDL_ZIP_API_NAME, API_VERSION, APIMIntegrationConstants.IS_API_EXISTS);
        restAPIPublisher.changeAPILifeCycleStatus(zipFileApiId, Constants.PUBLISHED);
        HttpResponse createdApiResponse2 = restAPIPublisher.getAPI(zipFileApiId);
        assertEquals(Response.Status.OK.getStatusCode(), createdApiResponse2.getResponseCode());

        // Update additional properties object
        additionalPropertiesObj.put("name", WSDL_URL_API_NAME);
        additionalPropertiesObj.put("context", WSDL_URL_API_CONTEXT);

        // Create API by WSDL URL
        APIDTO wsdlUrlApidto = restAPIPublisher
                .importWSDLSchemaDefinition(null, wsdlURL, additionalPropertiesObj.toString(), "SOAP");

        // Make sure API is created properly
        assertEquals(wsdlUrlApidto.getName(), WSDL_URL_API_NAME);
        assertEquals(wsdlUrlApidto.getContext(), "/" + WSDL_URL_API_CONTEXT);
        wsdlUrlApiId = wsdlUrlApidto.getId();

        // Create Revision and Deploy to Gateway
        createAPIRevisionAndDeployUsingRest(wsdlUrlApiId, restAPIPublisher);
        waitForAPIDeploymentSync(userName, WSDL_URL_API_NAME, API_VERSION, APIMIntegrationConstants.IS_API_EXISTS);
        restAPIPublisher.changeAPILifeCycleStatus(wsdlUrlApiId, Constants.PUBLISHED);
        HttpResponse createdApiResponse3 = restAPIPublisher.getAPI(wsdlUrlApiId);
        assertEquals(Response.Status.OK.getStatusCode(), createdApiResponse3.getResponseCode());

        // Create application and subscribe to API
        ApplicationDTO applicationDTO = restAPIStore.addApplication(APPLICATION_NAME,
                APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED, "", "");
        applicationId = applicationDTO.getApplicationId();
        restAPIStore.subscribeToAPI(wsdlUrlApiId, applicationId, APIMIntegrationConstants.API_TIER.UNLIMITED);

        // Generate access token
        ArrayList<String> grantTypes = new ArrayList<>();
        grantTypes.add("client_credentials");
        ApplicationKeyDTO applicationKeyDTO = restAPIStore.generateKeys(applicationId, "3600", null,
                ApplicationKeyGenerateRequestDTO.KeyTypeEnum.PRODUCTION, null, grantTypes);
        assertNotNull(applicationKeyDTO.getToken(), "Unable to get access token");
        accessToken = applicationKeyDTO.getToken().getAccessToken();
    }

    @Test(groups = {"wso2.am"}, description = "Get WSDL API definition of the created API",
            dependsOnMethods = "testWsdlDefinitionImport")
    public void testGetWsdlDefinitions() throws Exception {
        log.info("testGetWsdlDefinition initiated");

        // get wsdl definition of the API created with .wsdl file
        ApiResponse<Void> wsdlFileFlowResponse = restAPIPublisher.getWSDLSchemaDefinitionOfAPI(wsdlFileApiId);
        assertEquals(Response.Status.OK.getStatusCode(), wsdlFileFlowResponse.getStatusCode());

        // get wsdl definition of the API created with .zip file
        ApiResponse<Void> zipFileFlowResponse = restAPIPublisher.getWSDLSchemaDefinitionOfAPI(zipFileApiId);
        assertEquals(Response.Status.OK.getStatusCode(), zipFileFlowResponse.getStatusCode());

        // get wsdl definition of the API created with wsdl url
        ApiResponse<Void> wsdlUrlFlowResponse = restAPIPublisher.getWSDLSchemaDefinitionOfAPI(wsdlUrlApiId);
        assertEquals(Response.Status.OK.getStatusCode(), wsdlUrlFlowResponse.getStatusCode());
    }

    @Test(groups = {"wso2.am"}, description = "Download WSDL API definition of the created API from the store",
            dependsOnMethods = "testGetWsdlDefinitions")
    public void testDownloadWsdlDefinitionsFromStore() throws Exception {
        log.info("testDownloadWsdlDefinitionFromStore initiated");
        String environmentName = Constants.GATEWAY_ENVIRONMENT;

        // wsdl definition of the API created with .wsdl file from the store
        org.wso2.am.integration.clients.store.api.ApiResponse<Void> wsdlFileFlowResponse = restAPIStore
                .downloadWSDLSchemaDefinitionOfAPI(wsdlFileApiId, environmentName);
        assertEquals(Response.Status.OK.getStatusCode(), wsdlFileFlowResponse.getStatusCode());

        // wsdl definition of the API created with .zip file from the store
        org.wso2.am.integration.clients.store.api.ApiResponse<Void> zipFileFlowResponse = restAPIStore
                .downloadWSDLSchemaDefinitionOfAPI(zipFileApiId, environmentName);
        assertEquals(Response.Status.OK.getStatusCode(), zipFileFlowResponse.getStatusCode());

        // wsdl definition of the API created with wsdl url from the store
        org.wso2.am.integration.clients.store.api.ApiResponse<Void> wsdlUrlFlowResponse = restAPIStore
                .downloadWSDLSchemaDefinitionOfAPI(wsdlUrlApiId, environmentName);
        assertEquals(Response.Status.OK.getStatusCode(), wsdlUrlFlowResponse.getStatusCode());
    }

    @Test(groups = {"wso2.am"}, description = "Test invoking Check Phone Number method",
            dependsOnMethods = "testDownloadWsdlDefinitionsFromStore")
    public void testInvokeCheckPhoneNumber() throws Exception {
        log.info("testInvokeCheckPhoneNumber initiated");
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/xml");
        headers.put("accept", "text/xml");
        headers.put("Authorization", "Bearer " + accessToken);
        HttpResponse invokeAPIResponse = HttpRequestUtil.doPost(new URL(getAPIInvocationURLHttp(WSDL_URL_API_CONTEXT,
                API_VERSION)), requestBody, headers);
        assertEquals(invokeAPIResponse.getResponseCode(), HTTP_RESPONSE_CODE_OK,
                "Unable to invoke Pass Through SOAP API");
    }

    private void startWiremockServer() {
        endpointPort = getAvailablePort();
        assertNotEquals(endpointPort, -1, "No available port in the range " + lowerPortLimit + "-" +
                upperPortLimit + " was found");
        wireMockServer = new WireMockServer(options().port(endpointPort));
        wireMockServer.stubFor(WireMock.get(urlEqualTo("/phoneverify/wsdl")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "text/xml").withBody(wsdlDefinition)));
        wireMockServer.stubFor(WireMock.post(urlEqualTo("/phoneverify")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "text/xml").withBody(responseBody)));
        wireMockServer.start();
    }

    /**
     * Find a free port to start backend WebSocket server in given port range
     *
     * @return Available Port Number
     */
    private int getAvailablePort() {
        while (lowerPortLimit < upperPortLimit) {
            if (isPortFree(lowerPortLimit)) {
                return lowerPortLimit;
            }
            lowerPortLimit++;
        }
        return -1;
    }

    /**
     * Check whether give port is available
     *
     * @param port Port Number
     * @return status
     */
    private boolean isPortFree(int port) {
        Socket s = null;
        try {
            s = new Socket(endpointHost, port);
            // something is using the port and has responded.
            return false;
        } catch (IOException e) {
            // port available
            return true;
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close connection ", e);
                }
            }
        }
    }

    @AfterClass(alwaysRun = true)
    void destroy() throws Exception {
        restAPIStore.deleteApplication(applicationId);
        undeployAndDeleteAPIRevisionsUsingRest(wsdlFileApiId, restAPIPublisher);
        undeployAndDeleteAPIRevisionsUsingRest(zipFileApiId, restAPIPublisher);
        undeployAndDeleteAPIRevisionsUsingRest(wsdlUrlApiId, restAPIPublisher);
        restAPIPublisher.deleteAPI(wsdlFileApiId);
        restAPIPublisher.deleteAPI(zipFileApiId);
        restAPIPublisher.deleteAPI(wsdlUrlApiId);
        wireMockServer.stop();
    }
}
