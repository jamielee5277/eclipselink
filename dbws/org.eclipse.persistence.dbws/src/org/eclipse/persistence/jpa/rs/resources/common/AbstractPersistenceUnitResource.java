/*******************************************************************************
 * Copyright (c) 2011, 2013 Oracle and/or its affiliates. All rights reserved.
 * This program and the accompanying materials are made available under the 
 * terms of the Eclipse Public License v1.0 and Eclipse Distribution License v. 1.0 
 * which accompanies this distribution. 
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 *
 ******************************************************************************/
package org.eclipse.persistence.jpa.rs.resources.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.xml.bind.JAXBException;

import org.eclipse.persistence.descriptors.ClassDescriptor;
import org.eclipse.persistence.eis.mappings.EISCompositeCollectionMapping;
import org.eclipse.persistence.exceptions.JPARSException;
import org.eclipse.persistence.internal.expressions.ConstantExpression;
import org.eclipse.persistence.internal.expressions.MapEntryExpression;
import org.eclipse.persistence.internal.helper.ClassConstants;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.Attribute;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.Descriptor;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.Link;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.LinkTemplate;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.PersistenceUnit;
import org.eclipse.persistence.internal.jpa.rs.metadata.model.Query;
import org.eclipse.persistence.internal.queries.MapContainerPolicy;
import org.eclipse.persistence.internal.queries.ReportItem;
import org.eclipse.persistence.internal.sessions.AbstractSession;
import org.eclipse.persistence.jpa.rs.DataStorage;
import org.eclipse.persistence.jpa.rs.PersistenceContext;
import org.eclipse.persistence.jpa.rs.util.JPARSLogger;
import org.eclipse.persistence.jpa.rs.util.StreamingOutputMarshaller;
import org.eclipse.persistence.jpa.rs.util.list.QueryList;
import org.eclipse.persistence.mappings.CollectionMapping;
import org.eclipse.persistence.mappings.DatabaseMapping;
import org.eclipse.persistence.mappings.ForeignReferenceMapping;
import org.eclipse.persistence.queries.DatabaseQuery;
import org.eclipse.persistence.queries.ReportQuery;
import org.eclipse.persistence.sessions.DatabaseRecord;

/**
 * @author gonural
 *
 */
public class AbstractPersistenceUnitResource extends AbstractResource {
    protected Response getDescriptorMetadata(String version, String persistenceUnit, String descriptorAlias, HttpHeaders headers, URI baseURI) {
        try {
            PersistenceContext context = getPersistenceContext(persistenceUnit, baseURI, version, null);
            if (context == null) {
                JPARSLogger.warning("jpars_could_not_find_persistence_context", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), persistenceUnit });
                return Response.status(Status.NOT_FOUND).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
            } else {
                ClassDescriptor descriptor = context.getServerSession().getDescriptorForAlias(descriptorAlias);
                if (descriptor == null) {
                    JPARSLogger.fine("jpars_could_not_find_entity_type", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), descriptorAlias, persistenceUnit });
                    return Response.status(Status.NOT_FOUND).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
                } else {
                    String mediaType = StreamingOutputMarshaller.mediaType(headers.getAcceptableMediaTypes()).toString();
                    Descriptor returnDescriptor = buildDescriptor(context, persistenceUnit, descriptor, baseURI.toString());
                    String result = null;
                    try {
                        result = marshallMetadata(returnDescriptor, mediaType);
                    } catch (JAXBException e) {
                        JPARSLogger.fine("exception_marshalling_entity_metadata", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), descriptorAlias, persistenceUnit, e.toString() });
                        return Response.status(Status.INTERNAL_SERVER_ERROR).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
                    }
                    return Response.ok(new StreamingOutputMarshaller(null, result, headers.getAcceptableMediaTypes())).build();
                }
            }
        } catch (Exception ex) {
            throw JPARSException.exceptionOccurred(ex);
        }
    }

    protected Response getQueriesMetadata(String version, String persistenceUnit, HttpHeaders headers, URI baseURI) {
        try {
            PersistenceContext context = getPersistenceContext(persistenceUnit, baseURI, version, null);
            if (context == null) {
                JPARSLogger.warning("jpars_could_not_find_persistence_context", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), persistenceUnit });
                return Response.status(Status.NOT_FOUND).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
            } else {
                List<Query> queries = new ArrayList<Query>();
                addQueries(queries, context, null);
                String mediaType = StreamingOutputMarshaller.mediaType(headers.getAcceptableMediaTypes()).toString();
                QueryList queryList = new QueryList();
                queryList.setList(queries);
                String result = null;
                try {
                    if (mediaType.equals(MediaType.APPLICATION_JSON)) {
                        result = marshallMetadata(queryList.getList(), mediaType);
                    } else {
                        result = marshallMetadata(queryList, mediaType);
                    }
                } catch (JAXBException e) {
                    JPARSLogger.fine("exception_marshalling_query_metadata", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), persistenceUnit, e.toString() });
                    return Response.status(Status.INTERNAL_SERVER_ERROR).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
                }
                return Response.ok(new StreamingOutputMarshaller(null, result, headers.getAcceptableMediaTypes())).build();
            }
        } catch (Exception ex) {
            throw JPARSException.exceptionOccurred(ex);
        }
    }

    protected Response getQueryMetadata(String version, String persistenceUnit, String queryName, HttpHeaders headers, URI baseURI) {
        try {
            PersistenceContext context = getPersistenceContext(persistenceUnit, baseURI, version, null);
            if (context == null) {
                JPARSLogger.warning("jpars_could_not_find_persistence_context", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), persistenceUnit });
                return Response.status(Status.NOT_FOUND).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
            } else {
                List<Query> returnQueries = new ArrayList<Query>();
                Map<String, List<DatabaseQuery>> queries = context.getServerSession().getQueries();
                if (queries.get(queryName) != null) {
                    for (DatabaseQuery query : queries.get(queryName)) {
                        returnQueries.add(getQuery(query, context));
                    }
                }
                String mediaType = StreamingOutputMarshaller.mediaType(headers.getAcceptableMediaTypes()).toString();
                QueryList queryList = new QueryList();
                queryList.setList(returnQueries);
                String result = null;
                if (mediaType.equals(MediaType.APPLICATION_JSON)) {
                    result = marshallMetadata(queryList.getList(), mediaType);
                } else {
                    result = marshallMetadata(queryList, mediaType);
                }
                return Response.ok(new StreamingOutputMarshaller(null, result, headers.getAcceptableMediaTypes())).build();
            }
        } catch (Exception ex) {
            throw JPARSException.exceptionOccurred(ex);
        }
    }

    @SuppressWarnings("rawtypes")
    public Response getTypes(String version, String persistenceUnit, HttpHeaders headers, URI baseURI) {
        try {
            PersistenceContext context = getPersistenceContext(persistenceUnit, baseURI, version, null);
            if (context == null) {
                JPARSLogger.warning("jpars_could_not_find_persistence_context", new Object[] { DataStorage.get(DataStorage.REQUEST_ID), persistenceUnit });
                return Response.status(Status.NOT_FOUND).type(StreamingOutputMarshaller.getResponseMediaType(headers)).build();
            } else {
                PersistenceUnit pu = new PersistenceUnit();
                pu.setPersistenceUnitName(persistenceUnit);
                Map<Class, ClassDescriptor> descriptors = context.getServerSession().getDescriptors();
                String mediaType = StreamingOutputMarshaller.mediaType(headers.getAcceptableMediaTypes()).toString();
                Iterator<Class> contextIterator = descriptors.keySet().iterator();
                while (contextIterator.hasNext()) {
                    ClassDescriptor descriptor = descriptors.get(contextIterator.next());
                    String alias = descriptor.getAlias();
                    if (descriptor.isAggregateDescriptor()) {
                        // skip embeddables
                        continue;
                    }
                    if (version != null) {
                        pu.getTypes().add(new Link(alias, mediaType, baseURI + version + "/" + persistenceUnit + "/metadata/entity/" + alias));
                    } else {
                        pu.getTypes().add(new Link(alias, mediaType, baseURI + persistenceUnit + "/metadata/entity/" + alias));
                    }
                }
                String result = null;
                result = marshallMetadata(pu, mediaType);
                ResponseBuilder rb = Response.ok(new StreamingOutputMarshaller(null, result, headers.getAcceptableMediaTypes()));
                rb.header("Content-Type", MediaType.APPLICATION_JSON);
                return rb.build();
            }
        } catch (Exception ex) {
            throw JPARSException.exceptionOccurred(ex);
        }
    }

    @SuppressWarnings("rawtypes")
    protected void addMapping(Descriptor descriptor, DatabaseMapping mapping) {
        String target = null;
        String collectionName = null;
        if (mapping.isCollectionMapping()) {
            if (mapping.isEISMapping()) {
                EISCompositeCollectionMapping collectionMapping = (EISCompositeCollectionMapping) mapping;
                Class collectionClass = collectionMapping.getContainerPolicy().getContainerClass();
                String collectionType = getSimplePublicCollectionTypeName(collectionClass);
                if (collectionType == null) {
                    collectionType = collectionClass.getSimpleName();
                }
                if (collectionMapping.getReferenceClass() != null) {
                    collectionName = collectionMapping.getReferenceClass().getSimpleName();
                }
                if ((collectionName == null) && (collectionMapping.getAttributeClassification() != null)) {
                    collectionName = collectionMapping.getAttributeClassification().getSimpleName();
                }
                if (collectionMapping.getContainerPolicy().isMapPolicy()) {
                    String mapKeyType = ((MapContainerPolicy) collectionMapping.getContainerPolicy()).getKeyType().getClass().getSimpleName();
                    target = collectionType + "<" + mapKeyType + ", " + collectionName + ">";
                } else {
                    target = collectionType + "<" + collectionName + ">";
                }
            } else {
                CollectionMapping collectionMapping = (CollectionMapping) mapping;
                Class collectionClass = collectionMapping.getContainerPolicy().getContainerClass();

                String collectionType = getSimplePublicCollectionTypeName(collectionClass);
                if (collectionType == null) {
                    collectionType = collectionClass.getSimpleName();
                }

                if (collectionMapping.getReferenceClass() != null) {
                    collectionName = collectionMapping.getReferenceClass().getSimpleName();
                }
                if ((collectionName == null) && (collectionMapping.getAttributeClassification() != null)) {
                    collectionName = collectionMapping.getAttributeClassification().getSimpleName();
                }
                if (collectionMapping.getContainerPolicy().isMapPolicy()) {
                    String mapKeyType = ((MapContainerPolicy) collectionMapping.getContainerPolicy()).getKeyType().getClass().getSimpleName();
                    target = collectionType + "<" + mapKeyType + ", " + collectionName + ">";
                } else {
                    target = collectionType + "<" + collectionName + ">";
                }
            }
        } else if (mapping.isForeignReferenceMapping()) {
            target = ((ForeignReferenceMapping) mapping).getReferenceClass().getSimpleName();
        } else {
            target = mapping.getAttributeClassification().getSimpleName();
        }

        descriptor.getAttributes().add(new Attribute(mapping.getAttributeName(), target));
    }

    protected void addQueries(List<Query> queryList, PersistenceContext context, String javaClassName) {
        Map<String, List<DatabaseQuery>> queries = context.getServerSession().getQueries();
        List<DatabaseQuery> returnQueries = new ArrayList<DatabaseQuery>();
        for (String key : queries.keySet()) {
            List<DatabaseQuery> keyQueries = queries.get(key);
            Iterator<DatabaseQuery> queryIterator = keyQueries.iterator();
            while (queryIterator.hasNext()) {
                DatabaseQuery query = queryIterator.next();
                if (javaClassName == null || (query.getReferenceClassName() != null && query.getReferenceClassName().equals(javaClassName))) {
                    returnQueries.add(query);
                }
            }
        }
        Iterator<DatabaseQuery> queryIterator = returnQueries.iterator();
        while (queryIterator.hasNext()) {
            queryList.add(getQuery(queryIterator.next(), context));
        }
    }

    protected Descriptor buildDescriptor(PersistenceContext context, String persistenceUnit, ClassDescriptor descriptor, String baseUri) {
        Descriptor returnDescriptor = new Descriptor();
        String name = descriptor.getAlias();
        returnDescriptor.setName(name);

        String version = context.getVersion();
        if (version != null) {
            version = version + "/";
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("find", "get", baseUri + version + persistenceUnit + "/entity/" + descriptor.getAlias() + "/{primaryKey}"));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("persist", "put", baseUri + version + persistenceUnit + "/entity/" + descriptor.getAlias()));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("update", "post", baseUri + version + persistenceUnit + "/entity/" + descriptor.getAlias()));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("delete", "delete", baseUri + version + persistenceUnit + "/entity/" + descriptor.getAlias() + "/{primaryKey}"));
        } else {
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("find", "get", baseUri + persistenceUnit + "/entity/" + descriptor.getAlias() + "/{primaryKey}"));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("persist", "put", baseUri + persistenceUnit + "/entity/" + descriptor.getAlias()));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("update", "post", baseUri + persistenceUnit + "/entity/" + descriptor.getAlias()));
            returnDescriptor.getLinkTemplates().add(new LinkTemplate("delete", "delete", baseUri + persistenceUnit + "/entity/" + descriptor.getAlias() + "/{primaryKey}"));
        }
        if (!descriptor.getMappings().isEmpty()) {
            Iterator<DatabaseMapping> mappingIterator = descriptor.getMappings().iterator();
            while (mappingIterator.hasNext()) {
                DatabaseMapping mapping = mappingIterator.next();
                addMapping(returnDescriptor, mapping);
            }
        }
        addQueries(returnDescriptor.getQueries(), context, descriptor.getJavaClassName());
        return returnDescriptor;
    }

    protected Query getQuery(DatabaseQuery query, PersistenceContext context) {
        String method = query.isReadQuery() ? "get" : "post";
        String jpql = query.getJPQLString() == null ? "" : query.getJPQLString();
        StringBuffer parameterString = new StringBuffer();
        Iterator<String> argumentsIterator = query.getArguments().iterator();
        while (argumentsIterator.hasNext()) {
            String argument = argumentsIterator.next();
            parameterString.append(";");
            parameterString.append(argument + "={" + argument + "}");
        }

        String version = context.getVersion();
        Query returnQuery = null;
        if (version != null) {
            returnQuery = new Query(query.getName(), jpql, new LinkTemplate("execute", method, context.getBaseURI() + version + "/" + context.getName() + "/query/" + query.getName() + parameterString));
        } else {
            returnQuery = new Query(query.getName(), jpql, new LinkTemplate("execute", method, context.getBaseURI() + context.getName() + "/query/" + query.getName() + parameterString));
        }
        if (query.isReportQuery()) {
            query.checkPrepare((AbstractSession) context.getServerSession(), new DatabaseRecord());
            for (ReportItem item : ((ReportQuery) query).getItems()) {
                if (item.getMapping() != null) {
                    if (item.getAttributeExpression() != null && item.getAttributeExpression().isMapEntryExpression()) {
                        if (((MapEntryExpression) item.getAttributeExpression()).shouldReturnMapEntry()) {
                            returnQuery.getReturnTypes().add(Map.Entry.class.getSimpleName());
                        } else {
                            returnQuery.getReturnTypes().add(((Class<?>) ((CollectionMapping) item.getMapping()).getContainerPolicy().getKeyType()).getSimpleName());
                        }
                    } else {
                        returnQuery.getReturnTypes().add(item.getMapping().getAttributeClassification().getSimpleName());
                    }
                } else if (item.getResultType() != null) {
                    returnQuery.getReturnTypes().add(item.getResultType().getSimpleName());
                } else if (item.getDescriptor() != null) {
                    returnQuery.getReturnTypes().add(item.getDescriptor().getJavaClass().getSimpleName());
                } else if (item.getAttributeExpression() != null && item.getAttributeExpression().isConstantExpression()) {
                    returnQuery.getReturnTypes().add(((ConstantExpression) item.getAttributeExpression()).getValue().getClass().getSimpleName());
                } else {
                    // Use Object.class by default.
                    returnQuery.getReturnTypes().add(ClassConstants.OBJECT.getSimpleName());
                }
            }
        } else {
            returnQuery.getReturnTypes().add(query.getReferenceClassName() == null ? "" : query.getReferenceClass().getSimpleName());
        }
        return returnQuery;
    }

    private String getSimplePublicCollectionTypeName(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        LinkedHashSet<Class<?>> all = new LinkedHashSet<Class<?>>();
        getInterfaces(clazz, all);
        ArrayList<Class<?>> list = new ArrayList<Class<?>>(all);
        for (int i = 0; i < all.size(); i++) {
            Class<?> clas = list.get(i);
            if (clas.getName().equals(List.class.getName())) {
                return List.class.getSimpleName();
            }
            if (clas.getName().equals(Map.class.getName())) {
                return Map.class.getSimpleName();
            }
            if (clas.getName().equals(Set.class.getName())) {
                return Set.class.getSimpleName();
            }
        }
        return null;
    }

    private void getInterfaces(Class<?> clazz, HashSet<Class<?>> interfaceList) {
        // java.lang.Class.getInterfaces returns all directly implemented interfaces 
        // and it doesn't walk the hierarchy to get all interfaces of all parent types. 
        // we walk through the hierarchy here
        while (clazz != null) {
            Class<?>[] interfaces = clazz.getInterfaces();
            for (Class<?> i : interfaces) {
                if (interfaceList.add(i)) {
                    getInterfaces(i, interfaceList);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
