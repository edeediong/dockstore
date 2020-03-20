/*
 *    Copyright 2019 OICR
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
package io.dockstore.webservice.core;

import java.util.Objects;

import javax.persistence.Entity;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

import io.dockstore.common.EntryType;
import io.swagger.annotations.ApiModel;

@ApiModel(value = "Service", description = "This describes one service in the dockstore as a special degenerate case of a workflow")
@Entity
@Table(name = "service")
@NamedQuery(name = "io.dockstore.webservice.core.Service.findAllPublishedPaths", query = "SELECT new io.dockstore.webservice.core.database.WorkflowPath(c.sourceControl, c.organization, c.repository, c.workflowName) from Service c where c.isPublished = true")
public class Service extends Workflow {

    public enum SubClass { DOCKER_COMPOSE, SWARM, KUBERNETES, HELM }

    @Override
    public Entry getParentEntry() {
        return null;
    }

    public EntryType getEntryType() {
        return EntryType.SERVICE;
    }

    @Override
    public void setParentEntry(Entry parentEntry) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Service");
    }

    @Override
    public boolean isIsChecker() {
        return false;
    }

    @Override
    public void setIsChecker(boolean isChecker) {
        throw new UnsupportedOperationException("cannot add a checker workflow to a Service");
    }

    public Event.Builder getEventBuilder() {
        return new Event.Builder().withService(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Service that = (Service)o;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getSourceControl(), that.getSourceControl()) && Objects.equals(getOrganization(), that.getOrganization()) && Objects.equals(getRepository(), that.getRepository()) && Objects.equals(getWorkflowName(), that.getWorkflowName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getSourceControl(), getOrganization(), getRepository(), getWorkflowName());
    }
}
