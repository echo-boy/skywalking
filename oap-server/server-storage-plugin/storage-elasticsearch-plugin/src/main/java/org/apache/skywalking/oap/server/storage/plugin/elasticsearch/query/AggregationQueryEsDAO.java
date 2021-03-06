/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.manual.instance.InstanceTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.query.entity.Order;
import org.apache.skywalking.oap.server.core.query.entity.TopNEntity;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.avg.Avg;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public class AggregationQueryEsDAO extends EsDAO implements IAggregationQueryDAO {

    public AggregationQueryEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<TopNEntity> getServiceTopN(String indexName, String valueCName, int topN, DownSampling downsampling,
                                           long startTB, long endTB, Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(Metrics.TIME_BUCKET).lte(endTB).gte(startTB));
        return aggregation(indexName, valueCName, sourceBuilder, topN, order);
    }

    @Override
    public List<TopNEntity> getAllServiceInstanceTopN(String indexName,
                                                      String valueCName,
                                                      int topN,
                                                      DownSampling downsampling,
                                                      long startTB,
                                                      long endTB,
                                                      Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(Metrics.TIME_BUCKET).lte(endTB).gte(startTB));
        return aggregation(indexName, valueCName, sourceBuilder, topN, order);
    }

    @Override
    public List<TopNEntity> getServiceInstanceTopN(String serviceId,
                                                   String indexName,
                                                   String valueCName,
                                                   int topN,
                                                   DownSampling downsampling,
                                                   long startTB,
                                                   long endTB,
                                                   Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(Metrics.TIME_BUCKET).lte(endTB).gte(startTB));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(InstanceTraffic.SERVICE_ID, serviceId));

        return aggregation(indexName, valueCName, sourceBuilder, topN, order);
    }

    @Override
    public List<TopNEntity> getAllEndpointTopN(String indexName, String valueCName, int topN, DownSampling downsampling,
                                               long startTB, long endTB, Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();
        sourceBuilder.query(QueryBuilders.rangeQuery(Metrics.TIME_BUCKET).lte(endTB).gte(startTB));
        return aggregation(indexName, valueCName, sourceBuilder, topN, order);
    }

    @Override
    public List<TopNEntity> getEndpointTopN(String serviceId,
                                            String indexName,
                                            String valueCName,
                                            int topN,
                                            DownSampling downsampling,
                                            long startTB,
                                            long endTB,
                                            Order order) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        sourceBuilder.query(boolQueryBuilder);

        boolQueryBuilder.must().add(QueryBuilders.rangeQuery(Metrics.TIME_BUCKET).lte(endTB).gte(startTB));
        boolQueryBuilder.must().add(QueryBuilders.termQuery(EndpointTraffic.SERVICE_ID, serviceId));

        return aggregation(indexName, valueCName, sourceBuilder, topN, order);
    }

    protected List<TopNEntity> aggregation(String indexName, String valueCName, SearchSourceBuilder sourceBuilder,
                                           int topN, Order order) throws IOException {
        boolean asc = false;
        if (order.equals(Order.ASC)) {
            asc = true;
        }

        TermsAggregationBuilder aggregationBuilder = aggregationBuilder(valueCName, topN, asc);

        sourceBuilder.aggregation(aggregationBuilder);

        SearchResponse response = getClient().search(indexName, sourceBuilder);

        List<TopNEntity> topNEntities = new ArrayList<>();
        Terms idTerms = response.getAggregations().get(Metrics.ENTITY_ID);
        for (Terms.Bucket termsBucket : idTerms.getBuckets()) {
            TopNEntity topNEntity = new TopNEntity();
            topNEntity.setId(termsBucket.getKeyAsString());
            Avg value = termsBucket.getAggregations().get(valueCName);
            topNEntity.setValue((long) value.getValue());
            topNEntities.add(topNEntity);
        }

        return topNEntities;
    }

    protected TermsAggregationBuilder aggregationBuilder(final String valueCName, final int topN, final boolean asc) {
        return AggregationBuilders.terms(Metrics.ENTITY_ID)
                                  .field(Metrics.ENTITY_ID)
                                  .order(BucketOrder.aggregation(valueCName, asc))
                                  .size(topN)
                                  .subAggregation(AggregationBuilders.avg(valueCName).field(valueCName));
    }
}
