/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.mbeans;


import java.util.Iterator;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.OperationNotSupportedException;

import org.apache.catalina.Group;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.User;
import org.apache.catalina.UserDatabase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of <code>LifecycleListener</code> that instantiates the set of MBeans associated with global JNDI
 * resources that are subject to management.
 * <p>
 * This listener must only be nested within {@link Server} elements.
 *
 * @author Craig R. McClanahan
 *
 * @since 4.1
 */
public class GlobalResourcesLifecycleListener implements LifecycleListener {

    private static final Log log = LogFactory.getLog(GlobalResourcesLifecycleListener.class);
    protected static final StringManager sm = StringManager.getManager(GlobalResourcesLifecycleListener.class);


    // ----------------------------------------------------- Instance Variables

    /**
     * The owning Catalina component that we are attached to.
     */
    protected Lifecycle component = null;


    // ---------------------------------------------- LifecycleListener Methods

    /**
     * Primary entry point for startup and shutdown events.
     *
     * @param event The event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        if (Lifecycle.START_EVENT.equals(event.getType())) {
            if (!(event.getLifecycle() instanceof Server)) {
                log.warn(sm.getString("listener.notServer", event.getLifecycle().getClass().getSimpleName()));
            }
            component = event.getLifecycle();
            createMBeans();
        } else if (Lifecycle.STOP_EVENT.equals(event.getType())) {
            destroyMBeans();
            component = null;
        }
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Create the MBeans for the interesting global JNDI resources.
     */
    protected void createMBeans() {
        // Look up our global naming context
        Context context;
        try {
            context = (Context) (new InitialContext()).lookup("java:/");
        } catch (NamingException e) {
            log.error(sm.getString("globalResources.noNamingContext"));
            return;
        }

        // Recurse through the defined global JNDI resources context
        try {
            createMBeans("", context);
        } catch (NamingException e) {
            log.error(sm.getString("globalResources.createError"), e);
        }
    }


    /**
     * Create the MBeans for the interesting global JNDI resources in the specified naming context.
     *
     * @param prefix  Prefix for complete object name paths
     * @param context Context to be scanned
     *
     * @exception NamingException if a JNDI exception occurs
     */
    protected void createMBeans(String prefix, Context context) throws NamingException {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("globalResources.create", prefix));
        }

        try {
            NamingEnumeration<Binding> bindings = context.listBindings("");
            while (bindings.hasMore()) {
                Binding binding = bindings.next();
                String name = prefix + binding.getName();
                Object value = context.lookup(binding.getName());
                if (log.isTraceEnabled()) {
                    log.trace("Checking resource " + name);
                }
                if (value instanceof Context) {
                    createMBeans(name + "/", (Context) value);
                } else if (value instanceof UserDatabase) {
                    try {
                        createMBeans(name, (UserDatabase) value);
                    } catch (Exception e) {
                        log.error(sm.getString("globalResources.userDatabaseCreateError", name), e);
                    }
                }
            }
        } catch (RuntimeException ex) {
            log.error(sm.getString("globalResources.createError.runtime"), ex);
        } catch (OperationNotSupportedException ex) {
            log.error(sm.getString("globalResources.createError.operation"), ex);
        }
    }


    /**
     * Create the MBeans for the specified UserDatabase and its contents.
     *
     * @param name     Complete resource name of this UserDatabase
     * @param database The UserDatabase to be processed
     *
     * @exception Exception if an exception occurs while creating MBeans
     */
    protected void createMBeans(String name, UserDatabase database) throws Exception {

        // Create the MBean for the UserDatabase itself
        if (log.isTraceEnabled()) {
            log.trace("Creating UserDatabase MBeans for resource " + name);
            log.trace("Database=" + database);
        }
        try {
            MBeanUtils.createMBean(database);
        } catch (Exception e) {
            throw new IllegalArgumentException(sm.getString("globalResources.createError.userDatabase", name), e);
        }

        if (database.isSparse()) {
            // Avoid loading all the database as mbeans
            return;
        }

        // Create the MBeans for each defined Role
        Iterator<Role> roles = database.getRoles();
        while (roles.hasNext()) {
            Role role = roles.next();
            if (log.isTraceEnabled()) {
                log.trace("  Creating Role MBean for role " + role);
            }
            try {
                MBeanUtils.createMBean(role);
            } catch (Exception e) {
                throw new IllegalArgumentException(sm.getString("globalResources.createError.userDatabase.role", role),
                        e);
            }
        }

        // Create the MBeans for each defined Group
        Iterator<Group> groups = database.getGroups();
        while (groups.hasNext()) {
            Group group = groups.next();
            if (log.isTraceEnabled()) {
                log.trace("  Creating Group MBean for group " + group);
            }
            try {
                MBeanUtils.createMBean(group);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        sm.getString("globalResources.createError.userDatabase.group", group), e);
            }
        }

        // Create the MBeans for each defined User
        Iterator<User> users = database.getUsers();
        while (users.hasNext()) {
            User user = users.next();
            if (log.isTraceEnabled()) {
                log.trace("  Creating User MBean for user " + user);
            }
            try {
                MBeanUtils.createMBean(user);
            } catch (Exception e) {
                throw new IllegalArgumentException(sm.getString("globalResources.createError.userDatabase.user", user),
                        e);
            }
        }
    }


    /**
     * Destroy the MBeans for the interesting global JNDI resources.
     */
    protected void destroyMBeans() {
        if (log.isTraceEnabled()) {
            log.trace("Destroying MBeans for Global JNDI Resources");
        }
    }
}
