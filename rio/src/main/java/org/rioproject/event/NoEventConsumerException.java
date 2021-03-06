/*
 * Copyright 2008 the original author or authors.
 * Copyright 2005 Sun Microsystems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rioproject.event;

/**
 * Exception thrown when there are no registered event consumers.
 *
 * @author Dennis Reedy
 */
public class NoEventConsumerException extends Exception {
    static final long serialVersionUID = 1L;

    /**
     * Constructs an NoEventConsumerException with the specified detail message.
     * @param reason Detail message
     */
    public NoEventConsumerException(String reason) {
        super(reason);
    }

    /**
     * Constructs an NoEventConsumerException with no detail message.
     */
    public NoEventConsumerException() {
        super();
    }
}
