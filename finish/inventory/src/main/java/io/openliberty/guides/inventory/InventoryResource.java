// tag::copyright[]
/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Initial implementation
 *******************************************************************************/
// end::copyright[]
package io.openliberty.guides.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.reactivestreams.Publisher;

import io.openliberty.guides.models.Reservation;
import io.openliberty.guides.models.SystemLoad;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableEmitter;


@ApplicationScoped
//tag::inventoryEndPoint[]
@Path("/inventory")
//end::inventoryEndPoint[]
public class InventoryResource {

    private static Logger logger = Logger.getLogger(InventoryResource.class.getName());
    // tag::flowableEmitterDecl[]
    private FlowableEmitter<Reservation> propertyNameEmitter;
    // end::flowableEmitterDecl[]

    @Inject
    private InventoryManager manager;
    
    @GET
    @Path("/systems")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystems() {
        List<Properties> systems = manager.getSystems()
                .values()
                .stream()
                .collect(Collectors.toList());
        return Response
                .status(Response.Status.OK)
                .entity(systems)
                .build();
    }

    @GET
    @Path("/systems/{hostname}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSystem(@PathParam("hostname") String hostname) {
        Optional<Properties> system = manager.getSystem(hostname);
        if (system.isPresent()) {
            return Response
                    .status(Response.Status.OK)
                    .entity(system)
                    .build();
        }
        return Response
                .status(Response.Status.NOT_FOUND)
                .entity("hostname does not exist.")
                .build();
    }
    
    @POST
    // tag::postPath[]
    @Path("/systems/property")
    // end::postPath[]
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.TEXT_PLAIN)
    // tag::getSystemProperty[]
    public Response getSystemProperty(String propertyName) {
        logger.info("getSystemProperty: " + propertyName);
        // tag::flowableEmitter[]
       // propertyNameEmitter.onNext(propertyName);
        // end::flowableEmitter[]
        return Response
                   .status(Response.Status.OK)
                   .entity("Request successful for the " + propertyName + " property\n")
                   .build();
    }
    // end::getSystemProperty[]
    
    @GET
    @Path("/systems/reservation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getReservations() {
        Map<String, ArrayList<Properties>> reservations = manager.getReservations();
        return Response
                .status(Response.Status.OK)
                .entity(reservations)
                .build();
    }
    
    @POST
    // tag::postPath[]
    @Path("/systems/reservation")
    // end::postPath[]
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    // tag::getSystemProperty[]
    public Response addReservation(Reservation r) {
        logger.info("getSystemProperty: " + r.username);
        // tag::flowableEmitter[]
        propertyNameEmitter.onNext(r);
        // end::flowableEmitter[]
        return Response
                   .status(Response.Status.OK)
                   .entity("Request successful for the " + r.username + " property\n")
                   .build();
    }
    // end::getSystemProperty[]
    
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetSystems() {
        manager.resetSystems();
        return Response
                .status(Response.Status.OK)
                .build();
    }

    // tag::updateStatus[]
    // tag::systemLoad[]
    @Incoming("systemLoad")
    // end::systemLoad[]
    public void updateStatus(SystemLoad sl)  {
        String hostname = sl.hostname;
        if (manager.getSystem(hostname).isPresent()) {
            manager.updateCpuStatus(hostname, sl.loadAverage);
            logger.info("Host " + hostname + " was updated: " + sl);
        } else {
            manager.addSystem(hostname, sl.loadAverage);
            logger.info("Host " + hostname + " was added: " + sl);
        }
    }
    // end::updateStatus[]
    
    // tag::propertyMessage[]
    @Incoming("addSystemProperty")
    // end::propertyMessage[]
    public void getReservationMessage(Reservation res)  {
        logger.info("getPropertyMessage: " + res);
        String hostName = res.hostname;
        if (manager.getReservation(hostName).isPresent()) {
            manager.updateReservation(hostName, res);
            logger.info("Host " + hostName + " was updated: " + res);
        } else {
            manager.addReservation(hostName, res);
            logger.info("Host " + hostName + " was added: " + res);
        }
    }
    
    // tag::OutgoingPropertyName[]
    @Outgoing("requestSystemProperty")
    // end::OutgoingPropertyName[]
    public Publisher<Reservation> sendPropertyName() {
        // tag::flowableCreate[]
        Flowable<Reservation> flowable = Flowable.<Reservation>create(emitter -> 
            this.propertyNameEmitter = emitter, BackpressureStrategy.BUFFER);
        // end::flowableCreate[]
        return flowable;
    }
}
