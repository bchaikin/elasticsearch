/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.explain;

import org.elasticsearch.action.explain.ExplainResponse;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.indices.IndexMissingException;
import org.elasticsearch.AbstractSharedClusterTest;
import org.junit.Test;

import java.util.Map;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 */
public class ExplainActionTests extends AbstractSharedClusterTest {


    @Test
    public void testSimple() throws Exception {
        cluster().ensureAtLeastNumNodes(2);
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (IndexMissingException e) {
        }
        client().admin().indices().prepareCreate("test").setSettings(
                ImmutableSettings.settingsBuilder().put("index.refresh_interval", -1)
        ).execute().actionGet();
        client().admin().cluster().prepareHealth("test").setWaitForGreenStatus().execute().actionGet();

        client().prepareIndex("test", "test", "1")
                .setSource("field", "value1")
                .execute().actionGet();

        ExplainResponse response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        assertNotNull(response);
        assertFalse(response.isExists()); // not a match b/c not realtime
        assertFalse(response.isMatch()); // not a match b/c not realtime

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().isMatch());
        assertThat(response.getExplanation().getValue(), equalTo(1.0f));

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.termQuery("field", "value2"))
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isExists());
        assertFalse(response.isMatch());
        assertNotNull(response.getExplanation());
        assertFalse(response.getExplanation().isMatch());

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.boolQuery()
                        .must(QueryBuilders.termQuery("field", "value1"))
                        .must(QueryBuilders.termQuery("field", "value2"))
                )
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isExists());
        assertFalse(response.isMatch());
        assertNotNull(response.getExplanation());
        assertFalse(response.getExplanation().isMatch());
        assertThat(response.getExplanation().getDetails().length, equalTo(2));

        response = client().prepareExplain("test", "test", "2")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        assertNotNull(response);
        assertFalse(response.isExists());
        assertFalse(response.isMatch());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExplainWithFields() throws Exception {
        cluster().ensureAtLeastNumNodes(2);
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (IndexMissingException e) {
        }
        client().admin().indices().prepareCreate("test").execute().actionGet();
        client().admin().cluster().prepareHealth("test").setWaitForGreenStatus().execute().actionGet();

        client().prepareIndex("test", "test", "1")
                .setSource(
                        jsonBuilder().startObject()
                                .startObject("obj1")
                                .field("field1", "value1")
                                .field("field2", "value2")
                                .endObject()
                                .endObject()
                ).execute().actionGet();

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        ExplainResponse response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .setFields("obj1.field1")
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().isMatch());
        assertThat(response.getExplanation().getValue(), equalTo(1.0f));
        assertThat(response.getGetResult().isExists(), equalTo(true));
        assertThat(response.getGetResult().getId(), equalTo("1"));
        assertThat(response.getGetResult().getFields().size(), equalTo(1));
        assertThat(response.getGetResult().getFields().get("obj1.field1").getValue().toString(), equalTo("value1"));
        assertThat(response.getGetResult().isSourceEmpty(), equalTo(true));

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .setFields("obj1.field1")
                .setFetchSource(true)
                .get();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().isMatch());
        assertThat(response.getExplanation().getValue(), equalTo(1.0f));
        assertThat(response.getGetResult().isExists(), equalTo(true));
        assertThat(response.getGetResult().getId(), equalTo("1"));
        assertThat(response.getGetResult().getFields().size(), equalTo(1));
        assertThat(response.getGetResult().getFields().get("obj1.field1").getValue().toString(), equalTo("value1"));
        assertThat(response.getGetResult().isSourceEmpty(), equalTo(false));

        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .setFields("_source.obj1")
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertThat(response.getGetResult().getFields().size(), equalTo(1));
        Map<String, String> fields = (Map<String, String>) response.getGetResult().field("_source.obj1").getValue();
        assertThat(fields.size(), equalTo(2));
        assertThat(fields.get("field1"), equalTo("value1"));
        assertThat(fields.get("field2"), equalTo("value2"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testExplainWitSource() throws Exception {
        cluster().ensureAtLeastNumNodes(2);
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (IndexMissingException e) {
        }
        client().admin().indices().prepareCreate("test").execute().actionGet();
        client().admin().cluster().prepareHealth("test").setWaitForGreenStatus().execute().actionGet();

        client().prepareIndex("test", "test", "1")
                .setSource(
                        jsonBuilder().startObject()
                                .startObject("obj1")
                                .field("field1", "value1")
                                .field("field2", "value2")
                                .endObject()
                                .endObject()
                ).execute().actionGet();

        client().admin().indices().prepareRefresh("test").execute().actionGet();
        ExplainResponse response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .setFetchSource("obj1.field1", null)
                .get();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertNotNull(response.getExplanation());
        assertTrue(response.getExplanation().isMatch());
        assertThat(response.getExplanation().getValue(), equalTo(1.0f));
        assertThat(response.getGetResult().isExists(), equalTo(true));
        assertThat(response.getGetResult().getId(), equalTo("1"));
        assertThat(response.getGetResult().getSource().size(), equalTo(1));
        assertThat(((Map<String, Object>) response.getGetResult().getSource().get("obj1")).get("field1").toString(), equalTo("value1"));

        response = client().prepareExplain("test", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .setFetchSource(null, "obj1.field2")
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isMatch());
        assertThat(((Map<String, Object>) response.getGetResult().getSource().get("obj1")).get("field1").toString(), equalTo("value1"));
    }


    @Test
    public void testExplainWithAlias() throws Exception {
        cluster().ensureAtLeastNumNodes(2);
        try {
            client().admin().indices().prepareDelete("test").execute().actionGet();
        } catch (IndexMissingException e) {
        }
        client().admin().indices().prepareCreate("test")
                .execute().actionGet();
        client().admin().cluster().prepareHealth("test").setWaitForGreenStatus().execute().actionGet();

        client().admin().indices().prepareAliases().addAlias("test", "alias1", FilterBuilders.termFilter("field2", "value2"))
                .execute().actionGet();
        client().prepareIndex("test", "test", "1").setSource("field1", "value1", "field2", "value1").execute().actionGet();
        client().admin().indices().prepareRefresh("test").execute().actionGet();

        ExplainResponse response = client().prepareExplain("alias1", "test", "1")
                .setQuery(QueryBuilders.matchAllQuery())
                .execute().actionGet();
        assertNotNull(response);
        assertTrue(response.isExists());
        assertFalse(response.isMatch());
    }

}
