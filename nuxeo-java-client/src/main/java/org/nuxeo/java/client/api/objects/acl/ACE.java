/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *         Vladimir Pasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.java.client.api.objects.acl;

/**
 * @since 0.1
 */
public class ACE {

    protected String id;

    protected String username;

    protected String permission;

    protected String granted;

    protected String creator;

    protected String begin;

    protected String end;

    protected String status;

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPermission() {
        return permission;
    }

    public String getGranted() {
        return granted;
    }

    public String getCreator() {
        return creator;
    }

    public String getBegin() {
        return begin;
    }

    public String getEnd() {
        return end;
    }

    public String getStatus() {
        return status;
    }
}
