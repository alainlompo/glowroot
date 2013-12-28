/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.container.javaagent;

import java.io.IOException;
import java.util.List;

import checkers.igj.quals.ReadOnly;
import checkers.nullness.quals.Nullable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.glowroot.container.common.ObjectMappers;
import org.glowroot.container.config.AdvancedConfig;
import org.glowroot.container.config.CoarseProfilingConfig;
import org.glowroot.container.config.ConfigService;
import org.glowroot.container.config.FineProfilingConfig;
import org.glowroot.container.config.GeneralConfig;
import org.glowroot.container.config.PluginConfig;
import org.glowroot.container.config.PointcutConfig;
import org.glowroot.container.config.StorageConfig;
import org.glowroot.container.config.UserInterfaceConfig;
import org.glowroot.container.config.UserOverridesConfig;
import org.glowroot.markers.ThreadSafe;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@ThreadSafe
class JavaagentConfigService implements ConfigService {

    @ReadOnly
    private static final ObjectMapper mapper = ObjectMappers.create();

    private final JavaagentHttpClient httpClient;
    private final PortChangeListener portChangeListener;

    JavaagentConfigService(JavaagentHttpClient httpClient, PortChangeListener portChangeListener) {
        this.httpClient = httpClient;
        this.portChangeListener = portChangeListener;
    }

    public void setStoreThresholdMillis(int storeThresholdMillis) throws Exception {
        GeneralConfig generalConfig = getGeneralConfig();
        generalConfig.setStoreThresholdMillis(storeThresholdMillis);
        updateGeneralConfig(generalConfig);
    }

    public GeneralConfig getGeneralConfig() throws Exception {
        return getConfig("/backend/config/general", GeneralConfig.class);
    }

    public void updateGeneralConfig(GeneralConfig config) throws Exception {
        httpClient.post("/backend/config/general", mapper.writeValueAsString(config));
    }

    public CoarseProfilingConfig getCoarseProfilingConfig() throws Exception {
        return getConfig("/backend/config/coarse-profiling", CoarseProfilingConfig.class);
    }

    public void updateCoarseProfilingConfig(CoarseProfilingConfig config) throws Exception {
        httpClient.post("/backend/config/coarse-profiling", mapper.writeValueAsString(config));
    }

    public FineProfilingConfig getFineProfilingConfig() throws Exception {
        return getConfig("/backend/config/fine-profiling", FineProfilingConfig.class);
    }

    public void updateFineProfilingConfig(FineProfilingConfig config) throws Exception {
        httpClient.post("/backend/config/fine-profiling", mapper.writeValueAsString(config));
    }

    public UserOverridesConfig getUserOverridesConfig() throws Exception {
        return getConfig("/backend/config/user-overrides", UserOverridesConfig.class);
    }

    public void updateUserOverridesConfig(UserOverridesConfig config) throws Exception {
        httpClient.post("/backend/config/user-overrides", mapper.writeValueAsString(config));
    }

    public StorageConfig getStorageConfig() throws Exception {
        return getConfig("/backend/config/storage", StorageConfig.class);
    }

    public void updateStorageConfig(StorageConfig config) throws Exception {
        httpClient.post("/backend/config/storage", mapper.writeValueAsString(config));
    }

    public UserInterfaceConfig getUserInterfaceConfig() throws Exception {
        return getConfig("/backend/config/user-interface", UserInterfaceConfig.class);
    }

    public void updateUserInterfaceConfig(UserInterfaceConfig config) throws Exception {
        String response = httpClient.post("/backend/config/user-interface",
                mapper.writeValueAsString(config));
        JsonNode node = mapper.readTree(response);
        JsonNode currentPasswordIncorrectNode = node.get("currentPasswordIncorrect");
        if (currentPasswordIncorrectNode != null && currentPasswordIncorrectNode.asBoolean()) {
            throw new CurrentPasswordIncorrectException();
        }
        JsonNode portChangeFailedNode = node.get("portChangeFailed");
        if (portChangeFailedNode != null && portChangeFailedNode.asBoolean()) {
            throw new PortChangeFailedException();
        }
        portChangeListener.onMaybePortChange();
    }

    public AdvancedConfig getAdvancedConfig() throws Exception {
        return getConfig("/backend/config/advanced", AdvancedConfig.class);
    }

    public void updateAdvancedConfig(AdvancedConfig config) throws Exception {
        httpClient.post("/backend/config/advanced", mapper.writeValueAsString(config));
    }

    @Nullable
    public PluginConfig getPluginConfig(String pluginId) throws Exception {
        String response = httpClient.get("/backend/config/plugin/" + pluginId);
        ObjectNode rootNode = ObjectMappers.readRequiredValue(mapper, response, ObjectNode.class);
        JsonNode configNode = rootNode.get("config");
        return mapper.readValue(mapper.treeAsTokens(configNode), PluginConfig.class);
    }

    public void updatePluginConfig(String pluginId, PluginConfig config) throws Exception {
        httpClient.post("/backend/config/plugin/" + pluginId, mapper.writeValueAsString(config));
    }

    public List<PointcutConfig> getPointcutConfigs() throws Exception {
        String response = httpClient.get("/backend/config/pointcut");
        ObjectNode rootNode = ObjectMappers.readRequiredValue(mapper, response, ObjectNode.class);
        JsonNode configNode = rootNode.get("configs");
        return mapper.readValue(mapper.treeAsTokens(configNode),
                new TypeReference<List<PointcutConfig>>() {});
    }

    // returns new version
    public String addPointcutConfig(PointcutConfig pointcutConfig) throws Exception {
        String response = httpClient.post("/backend/config/pointcut/+",
                mapper.writeValueAsString(pointcutConfig));
        ObjectNode rootNode = ObjectMappers.readRequiredValue(mapper, response, ObjectNode.class);
        return rootNode.get("version").asText();
    }

    public void updatePointcutConfig(String version, PointcutConfig pointcutConfig)
            throws Exception {
        httpClient.post("/backend/config/pointcut/" + version,
                mapper.writeValueAsString(pointcutConfig));
    }

    public void removePointcutConfig(String version) throws Exception {
        httpClient.post("/backend/config/pointcut/-", mapper.writeValueAsString(version));
    }

    public void reweavePointcutConfigs() throws Exception {
        httpClient.post("/backend/admin/pointcuts/reweave", "");
    }

    public void compactData() throws Exception {
        httpClient.post("/backend/admin/data/compact", "");
    }

    void resetAllConfig() throws Exception {
        httpClient.post("/backend/admin/config/reset-all", "");
    }

    private <T> T getConfig(String url, Class<T> type) throws Exception, IOException,
            JsonProcessingException {
        String response = httpClient.get(url);
        ObjectNode rootNode = ObjectMappers.readRequiredValue(mapper, response, ObjectNode.class);
        JsonNode configNode = rootNode.get("config");
        return mapper.treeToValue(configNode, type);
    }

    interface PortChangeListener {
        void onMaybePortChange();
    }
}