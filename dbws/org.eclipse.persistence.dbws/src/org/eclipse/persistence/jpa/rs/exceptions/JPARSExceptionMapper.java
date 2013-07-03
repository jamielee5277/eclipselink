/*******************************************************************************
 * Copyright (c) 2011, 2013 Oracle. All rights reserved.
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 
 * which accompanies this distribution. 
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *      tware - initial 
 ******************************************************************************/
package org.eclipse.persistence.jpa.rs.exceptions;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.eclipse.persistence.exceptions.JPARSException;
import org.eclipse.persistence.jpa.rs.DataStorage;
import org.eclipse.persistence.jpa.rs.util.JPARSLogger;

@Provider
public class JPARSExceptionMapper extends AbstractExceptionMapper implements ExceptionMapper<JPARSException> {
    public Response toResponse(JPARSException exception) {
        if (exception.getCause() != null) {
            JPARSLogger.exception("jpars_caught_exception", new Object[] { DataStorage.get(DataStorage.REQUEST_ID) }, (Exception) exception.getCause());
        } else {
            JPARSLogger.exception("jpars_caught_exception", new Object[] { DataStorage.get(DataStorage.REQUEST_ID) }, exception);
        }
        return buildResponse(exception);
    }
}
