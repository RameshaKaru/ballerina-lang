// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.


import ballerina/auth;
import ballerina/cache;
import ballerina/reflect;

# Representation of the Authorization filter
#
# + authzHandler - `HttpAuthzHandler` instance for handling authorization
public type AuthzFilter object {

    public HttpAuthzHandler authzHandler;

    public function __init(HttpAuthzHandler authzHandler) {
        self.authzHandler = authzHandler;
    }

    # Filter function implementation which tries to authorize the request
    #
    # + caller - Caller for outbound HTTP responses
    # + request - `Request` instance
    # + context - `FilterContext` instance
    # + return - A flag to indicate if the request flow should be continued(true) or aborted(false), a code and a message
    public function filterRequest (Caller caller, Request request, FilterContext context) returns boolean {
		// first check if the resource is marked to be authenticated. If not, no need to authorize.
        ListenerAuthConfig? resourceLevelAuthAnn = getAuthAnnotation(ANN_MODULE, RESOURCE_ANN_NAME,
            reflect:getResourceAnnotations(context.serviceRef, context.resourceName));
        ListenerAuthConfig? serviceLevelAuthAnn = getAuthAnnotation(ANN_MODULE, SERVICE_ANN_NAME,
            reflect:getServiceAnnotations(context.serviceRef));
        if (!isResourceSecured(resourceLevelAuthAnn, serviceLevelAuthAnn)) {
            // not secured, no need to authorize
            return isAuthzSuccessfull(caller, true);
        }

        string[]? scopes = getScopesForResource(resourceLevelAuthAnn, serviceLevelAuthAnn);
        boolean authorized;
        if (scopes is string[]) {
            if (scopes.length() > 0) {
                if (self.authzHandler.canHandle(request)) {
                    authorized = self.authzHandler.handle(runtime:getInvocationContext().principal.username,
                        context.serviceName, context.resourceName, request.method, scopes);
                } else {
                    authorized = false;
                }
            } else {
                // scopes are not defined, no need to authorize
                authorized = true;
            }
        } else {
            // scopes are not defined, no need to authorize
            authorized = true;
        }
        return isAuthzSuccessfull(caller, authorized);
    }

    public function filterResponse(Response response, FilterContext context) returns boolean {
        return true;
    }
};

# Verifies if the authorization is successful. If not responds to the user.
#
# + caller - Caller for outbound HTTP responses
# + authorized - flag to indicate if authorization is successful or not
# + return - A boolean flag to indicate if the request flow should be continued(true) or
#            aborted(false)
function isAuthzSuccessfull(Caller caller, boolean authorized) returns boolean {
    Response response = new;
    if (!authorized) {
        response.statusCode = 403;
        response.setTextPayload("Authorization failure");
        var err = caller->respond(response);
        if (err is error) {
            panic err;
        }
        return false;
    }
    return true;
}

# Retrieves the scope for the resource, if any
#
# + resourceLevelAuthAnn - `ListenerAuthConfig` instance denoting resource level auth annotation details
# + serviceLevelAuthAnn - `ListenerAuthConfig` instance denoting service level auth annotation details
# + return - Array of scopes for the given resource or nil of no scopes are defined
function getScopesForResource (ListenerAuthConfig? resourceLevelAuthAnn, ListenerAuthConfig? serviceLevelAuthAnn)
                                                                                            returns (string[]|()) {
    if (resourceLevelAuthAnn.scopes is string[]) {
        return resourceLevelAuthAnn.scopes;
    } else {
        if (serviceLevelAuthAnn.scopes is string[]) {
            return serviceLevelAuthAnn.scopes;
        }
        return ();
    }
}
