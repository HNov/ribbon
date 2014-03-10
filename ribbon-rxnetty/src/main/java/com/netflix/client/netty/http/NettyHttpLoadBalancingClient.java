/*
 *
 * Copyright 2014 Netflix, Inc.
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
 *
 */
package com.netflix.client.netty.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import io.reactivex.netty.protocol.http.client.RepeatableContentHttpRequest;
import io.reactivex.netty.protocol.text.sse.ServerSentEvent;

import javax.annotation.Nullable;

import rx.Observable;

import com.netflix.client.ClientObservableProvider;
import com.netflix.client.LoadBalancerExecutor;
import com.netflix.client.RequestSpecificRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerStats;
import com.netflix.serialization.HttpSerializationContext;
import com.netflix.serialization.SerializationFactory;
import com.netflix.serialization.TypeDef;

public class NettyHttpLoadBalancingClient extends NettyHttpClient {

    private final LoadBalancerExecutor lbObservables;
    private final NettyHttpClient delegate;

    public NettyHttpLoadBalancingClient() {
        this(null, DefaultClientConfigImpl.getClientConfigWithDefaultValues());
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config) {
        delegate = new NettyHttpClient(config);
        lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(new NettyHttpLoadBalancerErrorHandler(config));
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler) {
        delegate = new NettyHttpClient(config);
        lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    public NettyHttpLoadBalancingClient(ILoadBalancer lb, IClientConfig config, RetryHandler errorHandler, 
            SerializationFactory<HttpSerializationContext> serializationFactory, Bootstrap bootStrap) {
        delegate = new NettyHttpClient(config, serializationFactory, bootStrap);
        this.lbObservables = new LoadBalancerExecutor(lb, config);
        lbObservables.setErrorHandler(errorHandler);
    }
    
    @Override
    public IClientConfig getConfig() {
        return delegate.getConfig();
    }

    @Override
    public SerializationFactory<HttpSerializationContext> getSerializationFactory() {
        return delegate.getSerializationFactory();
    }

    private RequestSpecificRetryHandler getRequestRetryHandler(HttpClientRequest<?> request, IClientConfig requestConfig) {
        boolean okToRetryOnAllErrors = request.getMethod().equals(HttpMethod.GET);
        return new RequestSpecificRetryHandler(true, okToRetryOnAllErrors, lbObservables.getErrorHandler(), requestConfig);
    }
        
    public <I> HttpClientRequest<I> getRepeatableRequest(HttpClientRequest<I> original) {
        return new RepeatableContentHttpRequest<I>(original);
    }
    
    public <I, O> Observable<ServerSentEventWithEntity<O>> createServerSentEventEntityObservable(
            HttpClientRequest<I> request, final TypeDef<O> typeDef, final IClientConfig requestConfig, final Object loadBalancerKey) {
        final HttpClientRequest<I> repeatbleRequest = new RepeatableContentHttpRequest<I>(request);
        return lbObservables.executeWithLoadBalancer(new ClientObservableProvider<ServerSentEventWithEntity<O>>() {

            @Override
            public Observable<ServerSentEventWithEntity<O>> getObservableForEndpoint(Server server) {
                return delegate.createServerSentEventEntityObservable(server.getHost(), server.getPort(), repeatbleRequest, typeDef, requestConfig);
            }

        }, getRequestRetryHandler(request, requestConfig), loadBalancerKey);
    }

    public <I> Observable<HttpClientResponse<ServerSentEvent>> createServerSentEventObservable(
            final HttpClientRequest<I> request, final IClientConfig requestConfig, Object loadBalancerKey) {
        final HttpClientRequest<I> repeatbleRequest = new RepeatableContentHttpRequest<I>(request);
        return lbObservables.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ServerSentEvent>>() {

            @Override
            public Observable<HttpClientResponse<ServerSentEvent>> getObservableForEndpoint(Server server) {
                return delegate.createServerSentEventObservable(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
            
        }, getRequestRetryHandler(request, requestConfig), loadBalancerKey);
    }
    
    public <I> Observable<HttpClientResponse<ServerSentEvent>> createServerSentEventObservable(final HttpClientRequest<I> request) {
        return createServerSentEventObservable(request, null, null);
    }

    public <I> Observable<HttpClientResponse<ByteBuf>> createFullHttpResponseObservable(final HttpClientRequest<I> request) {
        return createFullHttpResponseObservable(request, null, null);
    }
    
    public <I> Observable<HttpClientResponse<ByteBuf>> createFullHttpResponseObservable(
            final HttpClientRequest<I> request, final IClientConfig requestConfig, Object loadBalancerKey) {
        final HttpClientRequest<I> repeatbleRequest = new RepeatableContentHttpRequest<I>(request);
        return lbObservables.executeWithLoadBalancer(new ClientObservableProvider<HttpClientResponse<ByteBuf>>() {

            @Override
            public Observable<HttpClientResponse<ByteBuf>> getObservableForEndpoint(
                    Server server) {
                System.err.println("Trying server: " + server);
                return delegate.createFullHttpResponseObservable(server.getHost(), server.getPort(), repeatbleRequest, requestConfig);
            }
            
        }, getRequestRetryHandler(request, requestConfig), loadBalancerKey);
    }

    public <I, O> Observable<O> createEntityObservable(final HttpClientRequest<I> request, final TypeDef<O> typeDef) {
        return createEntityObservable(request, typeDef, null, null, null);
    }
    
    public <I, O> Observable<O> createEntityObservable(final HttpClientRequest<I> request,
            final TypeDef<O> typeDef, @Nullable final IClientConfig requestConfig, @Nullable final RetryHandler retryHandler, @Nullable Object loadBalancerKey) {
        final RetryHandler handler = retryHandler == null ? 
                getRequestRetryHandler(request, requestConfig) : retryHandler;
                final HttpClientRequest<I> repeatbleRequest = new RepeatableContentHttpRequest<I>(request);
        return lbObservables.executeWithLoadBalancer(new ClientObservableProvider<O>() {

            @Override
            public Observable<O> getObservableForEndpoint(Server server) {
                return delegate.createEntityObservable(server.getHost(), server.getPort(), repeatbleRequest, typeDef, requestConfig);
            }
        }, handler, loadBalancerKey);
   }

    
    public <I, O> Observable<O> createEntityObservable(final HttpClientRequest<I> request,
            final TypeDef<O> typeDef, final IClientConfig requestConfig, Object loadBalancerKey) {
        return createEntityObservable(request, typeDef, requestConfig, null, loadBalancerKey);
   }

    public void setLoadBalancer(ILoadBalancer lb) {
        lbObservables.setLoadBalancer(lb);
    }
    
    public ILoadBalancer getLoadBalancer() {
        return lbObservables.getLoadBalancer();
    }
    
    public int getMaxAutoRetriesNextServer() {
        if (lbObservables.getErrorHandler() != null) {
            return lbObservables.getErrorHandler().getMaxRetriesOnNextServer();
        }
        return 0;
    }

    public int getMaxAutoRetries() {
        if (lbObservables.getErrorHandler() != null) {
            return lbObservables.getErrorHandler().getMaxRetriesOnSameServer();
        }
        return 0;
    }

    public ServerStats getServerStats(Server server) {
        return lbObservables.getServerStats(server);
    }
}
