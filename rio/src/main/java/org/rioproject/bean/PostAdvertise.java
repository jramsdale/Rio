/*
 * Copyright 2008 the original author or authors.
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
package org.rioproject.bean;

import java.lang.annotation.*;

/**
 * The PostAdvertise annotation is used on a method in a bean to allow the bean
 * to be notified after the bean has been advertised.
 *
 * <p>The method on which the PostAdvertise annotation is applied must fulfill
 * the following requirements:
 *
 * <ul>
 * <li>The method must not throw a checked exception
 * <li>The method on which PostAdvertise is applied must be public
 * <li>The method must not be static
 * </ul>
 *
 * @author Dennis Reedy
 */
@Documented
@Retention (RetentionPolicy.RUNTIME)
@Target (value= ElementType.METHOD)
public @interface PostAdvertise {
}
