/*
 *    Copyright 2016 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.swagger.quay.client.api;

import io.swagger.quay.client.ApiException;
import io.swagger.quay.client.ApiClient;
import io.swagger.quay.client.Configuration;
import io.swagger.quay.client.Pair;
import io.swagger.quay.client.TypeRef;

import io.swagger.quay.client.model.NewRepo;
import io.swagger.quay.client.model.RepoUpdate;
import io.swagger.quay.client.model.ChangeVisibility;

import java.util.*;

@javax.annotation.Generated(value = "class io.swagger.codegen.languages.JavaClientCodegen", date = "2016-03-23T15:13:48.378-04:00")
public class RepositoryApi {
  private ApiClient apiClient;

  public RepositoryApi() {
    this(Configuration.getDefaultApiClient());
  }

  public RepositoryApi(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  public ApiClient getApiClient() {
    return apiClient;
  }

  public void setApiClient(ApiClient apiClient) {
    this.apiClient = apiClient;
  }

  
  /**
   * 
   * Fetch the list of repositories visible to the current user under a variety of situations.
   * @param nextPage The page token for the next page
   * @param popularity Whether to include the repository&#39;s popularity metric.
   * @param lastModified Whether to include when the repository was last modified.
   * @param _public Adds any repositories visible to the user by virtue of being public
   * @param starred Filters the repositories returned to those starred by the user
   * @param namespace Filters the repositories returned to this namespace
   * @return void
   */
  public void listRepos (String nextPage, Boolean popularity, Boolean lastModified, Boolean _public, Boolean starred, String namespace) throws ApiException {
    Object postBody = null;
    
    // create path and map variables
    String path = "/api/v1/repository".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    
    queryParams.addAll(apiClient.parameterToPairs("", "next_page", nextPage));
    
    queryParams.addAll(apiClient.parameterToPairs("", "popularity", popularity));
    
    queryParams.addAll(apiClient.parameterToPairs("", "last_modified", lastModified));
    
    queryParams.addAll(apiClient.parameterToPairs("", "public", _public));
    
    queryParams.addAll(apiClient.parameterToPairs("", "starred", starred));
    
    queryParams.addAll(apiClient.parameterToPairs("", "namespace", namespace));
    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Create a new repository.
   * @param body Request body contents.
   * @return void
   */
  public void createRepo (NewRepo body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling createRepo");
    }
    
    // create path and map variables
    String path = "/api/v1/repository".replaceAll("\\{format\\}","json");

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Fetch the specified repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void getRepo (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling getRepo");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "GET", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Update the description in the specified repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void updateRepo (String repository, RepoUpdate body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling updateRepo");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling updateRepo");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "PUT", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Delete a repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @return void
   */
  public void deleteRepository (String repository) throws ApiException {
    Object postBody = null;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling deleteRepository");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "DELETE", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
  /**
   * 
   * Change the visibility of a repository.
   * @param repository The full path of the repository. e.g. namespace/name
   * @param body Request body contents.
   * @return void
   */
  public void changeRepoVisibility (String repository, ChangeVisibility body) throws ApiException {
    Object postBody = body;
    
    // verify the required parameter 'repository' is set
    if (repository == null) {
      throw new ApiException(400, "Missing the required parameter 'repository' when calling changeRepoVisibility");
    }
    
    // verify the required parameter 'body' is set
    if (body == null) {
      throw new ApiException(400, "Missing the required parameter 'body' when calling changeRepoVisibility");
    }
    
    // create path and map variables
    String path = "/api/v1/repository/{repository}/changevisibility".replaceAll("\\{format\\}","json")
      .replaceAll("\\{" + "repository" + "\\}", apiClient.escapeString(repository.toString()));

    // query params
    List<Pair> queryParams = new ArrayList<Pair>();
    Map<String, String> headerParams = new HashMap<String, String>();
    Map<String, Object> formParams = new HashMap<String, Object>();

    

    

    

    final String[] accepts = {
      
    };
    final String accept = apiClient.selectHeaderAccept(accepts);

    final String[] contentTypes = {
      
    };
    final String contentType = apiClient.selectHeaderContentType(contentTypes);

    String[] authNames = new String[] { "oauth2_implicit" };

    
    apiClient.invokeAPI(path, "POST", queryParams, postBody, headerParams, formParams, accept, contentType, authNames, null);
    
  }
  
}
