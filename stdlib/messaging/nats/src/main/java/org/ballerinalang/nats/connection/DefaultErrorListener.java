/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.nats.connection;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import org.ballerinalang.jvm.BallerinaErrors;
import org.ballerinalang.jvm.BallerinaValues;
import org.ballerinalang.jvm.Scheduler;
import org.ballerinalang.jvm.values.ArrayValue;
import org.ballerinalang.jvm.values.ErrorValue;
import org.ballerinalang.jvm.values.ObjectValue;
import org.ballerinalang.jvm.values.connector.Executor;
import org.ballerinalang.nats.Constants;
import org.ballerinalang.nats.basic.consumer.DefaultMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.ballerinalang.nats.Constants.NATS_ERROR_CODE;

/**
 * ErrorListener to track the status of a {@link Connection Connection}.
 *
 * @since 1.0.0
 */
public class DefaultErrorListener implements ErrorListener {

    private static final PrintStream console;
    private static final Logger LOG = LoggerFactory.getLogger(DefaultErrorListener.class);
    private final Scheduler scheduler;
    private final List<ObjectValue> serviceList;

    DefaultErrorListener(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.serviceList = Collections.synchronizedList(new ArrayList<>());
    }

    static {
        console = System.out;
    }

    @Override
    public void errorOccurred(Connection conn, String error) {
        String message = "Error in server " + conn.getConnectedUrl() + ". " + error;
        ErrorValue errorValue = BallerinaErrors.createError(NATS_ERROR_CODE, message);
        LOG.error(message);
        processErrorValue(errorValue);
    }

    @Override
    public void exceptionOccurred(Connection conn, Exception exp) {
        LOG.error("Exception in server " + conn.getConnectedUrl(), exp);
        String errorMsg = exp.getCause() != null ? exp.getCause().getMessage() : exp.getMessage();
        ErrorValue errorValue = BallerinaErrors.createError(NATS_ERROR_CODE, errorMsg);
        processErrorValue(errorValue);
    }

    private void processErrorValue(ErrorValue errorValue) {

        ArrayValue msgData = new ArrayValue(new byte[0]);
        ObjectValue msgObj = BallerinaValues.createObjectValue(Constants.NATS_PACKAGE,
                Constants.NATS_MESSAGE_OBJ_NAME, "", msgData, "");
        for (ObjectValue service : serviceList) {
            Executor.submit(scheduler, service, "onError", new DefaultMessageHandler.ResponseCallback(), null,
                    msgObj, Boolean.TRUE, errorValue, Boolean.TRUE);
        }
    }

    @Override
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        LOG.error("slow consumer detected in server " + conn.getConnectedUrl() + " | " + consumer.toString());
    }

    public void addServiceObject(ObjectValue serviceObject) {
        serviceList.add(serviceObject);
    }
}
