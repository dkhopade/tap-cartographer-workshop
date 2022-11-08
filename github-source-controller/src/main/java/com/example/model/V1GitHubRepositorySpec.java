/*
 * Kubernetes
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: v1.21.1
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */


package com.example.model;

import java.util.Objects;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * GitHubRepositorySpec defines the desired state of a GitHub repository.
 */
@ApiModel(description = "GitHubRepositorySpec defines the desired state of a GitHub repository.")
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2022-06-05T14:06:13.160Z[Etc/UTC]")
public class V1GitHubRepositorySpec {
  public static final String SERIALIZED_NAME_BRANCH = "branch";
  @SerializedName(SERIALIZED_NAME_BRANCH)
  private String branch;

  public static final String SERIALIZED_NAME_URL = "url";
  @SerializedName(SERIALIZED_NAME_URL)
  private String url;


  public V1GitHubRepositorySpec branch(String branch) {
    
    this.branch = branch;
    return this;
  }

   /**
   * The Git branch to checkout, defaults to main.
   * @return branch
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(value = "The Git branch to checkout, defaults to main.")

  public String getBranch() {
    return branch;
  }


  public void setBranch(String branch) {
    this.branch = branch;
  }


  public V1GitHubRepositorySpec url(String url) {
    
    this.url = url;
    return this;
  }

   /**
   * The repository URL, can be a HTTP/S or SSH address.
   * @return url
  **/
  @ApiModelProperty(required = true, value = "The repository URL, can be a HTTP/S or SSH address.")

  public String getUrl() {
    return url;
  }


  public void setUrl(String url) {
    this.url = url;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    V1GitHubRepositorySpec v1GitHubRepositorySpec = (V1GitHubRepositorySpec) o;
    return Objects.equals(this.branch, v1GitHubRepositorySpec.branch) &&
        Objects.equals(this.url, v1GitHubRepositorySpec.url);
  }

  @Override
  public int hashCode() {
    return Objects.hash(branch, url);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class V1GitHubRepositorySpec {\n");
    sb.append("    branch: ").append(toIndentedString(branch)).append("\n");
    sb.append("    url: ").append(toIndentedString(url)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}

