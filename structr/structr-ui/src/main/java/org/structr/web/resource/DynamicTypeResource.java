/*
 *  Copyright (C) 2012 Axel Morgner
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.resource;

import java.util.*;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.neo4j.graphdb.RelationshipType;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Command;
import org.structr.core.EntityContext;
import org.structr.core.GraphObject;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.node.*;
import org.structr.core.node.search.Search;
import org.structr.core.node.search.SearchAttribute;
import org.structr.core.node.search.SearchNodeCommand;
import org.structr.rest.RestMethodResult;
import org.structr.rest.exception.IllegalPathException;
import org.structr.rest.resource.Resource;
import org.structr.rest.resource.TypeResource;
import org.structr.rest.resource.UuidResource;
import org.structr.web.entity.Component;
import org.structr.web.entity.Content;

/**
 *
 * @author Christian Morgner
 */
public class DynamicTypeResource extends TypeResource {

	private static final Logger logger = Logger.getLogger(DynamicTypeResource.class.getName());
	private List<DynamicTypeResource> nestedResources = new ArrayList<DynamicTypeResource>();
	private UuidResource uuidResource = null;
	private boolean parentResults = false;
	private String resourceId = null;

	@Override
	public boolean checkAndConfigure(String part, SecurityContext securityContext, HttpServletRequest request) throws FrameworkException {

		this.securityContext = securityContext;
		this.request         = request;
		this.rawType         = part;

		super.checkAndConfigure(part, securityContext, request);
		
		// FIXME: do type check on existing dynamic resources here..
		return rawType != null;
	}

	
	@Override
	public List<GraphObject> doGet() throws FrameworkException {

		// REST path contained uuid, return result of UuidResource
		if(uuidResource != null) {
			// FIXME: type cast is redundant, fix return type of UuidResource instead!
			return (List<GraphObject>)uuidResource.doGet();
		}

		// check for dynamic type, use super class otherwise
		List<SearchAttribute> searchAttributes = new LinkedList<SearchAttribute>();
		AbstractNode topNode                   = null;
		boolean includeDeleted                 = false;
		boolean publicOnly                     = false;

		if (rawType != null) {

			searchAttributes.add(Search.andExactProperty(Component.UiKey.structrclass.name(), EntityContext.normalizeEntityName(rawType)));
			searchAttributes.add(Search.andExactType(Component.class.getSimpleName()));
			
			// searchable attributes from EntityContext
			hasSearchableAttributes(rawType, request, searchAttributes);

			// do search
			List<GraphObject> results = (List<GraphObject>) Services.command(securityContext, SearchNodeCommand.class).execute(topNode, includeDeleted, publicOnly, searchAttributes);
			if (!results.isEmpty()) {
				return results;

			}
		}
		
		parentResults = true;
			
		return super.doGet();
	}

	@Override
	public RestMethodResult doPost(final Map<String, Object> propertySet) throws FrameworkException {
		
		// REST path contained uuid, POST not allowed here
		if(uuidResource != null) {
			throw new IllegalPathException();
		}
		
		List<GraphObject> templates = doGet();
		if(parentResults) {
			
			return super.doPost(propertySet);
			
		} else if(!templates.isEmpty()) {

			final Command createNodeCommand = Services.command(securityContext, CreateNodeCommand.class);
			final Map<String, Object> templateProperties = new LinkedHashMap<String, Object>();
			final String componentId = UUID.randomUUID().toString().replaceAll("[\\-]+", "");
			final Component template = (Component)templates.get(0);
			final int position = templates.size();

			// copy properties to map
			templateProperties.put(AbstractNode.Key.type.name(), Component.class.getSimpleName());
			templateProperties.put("structrclass", template.getStringProperty("structrclass"));
			templateProperties.put("uuid", componentId);

			Component newComponent = (Component)Services.command(securityContext, TransactionCommand.class).execute(new StructrTransaction() {

				@Override
				public Object execute() throws FrameworkException {
					
					Component comp = (Component)createNodeCommand.execute(templateProperties);
					copyRelationships(template, comp, getResourceId(), null, position);
					
					Map<String, Object> contentTemplateProperties = new LinkedHashMap<String, Object>();
					for(AbstractNode node : template.getContentNodes().values()) {
						
						// copy content properties
						if(node instanceof Content) {
							
							Content contentTemplate = (Content)node;
							String dataKey = contentTemplate.getStringProperty("data-key");
			
							// create new content node with content from property set
							contentTemplateProperties.clear();
							contentTemplateProperties.put(AbstractNode.Key.type.name(), "Content");
							contentTemplateProperties.put("data-key", dataKey);
							contentTemplateProperties.put("content", propertySet.get(dataKey));
							Content newContent = (Content)createNodeCommand.execute(contentTemplateProperties);
							
							// remove non-local data key from set
							propertySet.remove(dataKey);

							copyRelationships(contentTemplate, newContent, getResourceId(), componentId, position);
						}
						
					}
					
					return comp;
				}
				
			});
			
			if(newComponent != null) {
				for(String key : propertySet.keySet()) {
					newComponent.setProperty(key, propertySet.get(key));
				}
			}
			
			RestMethodResult result = new RestMethodResult(201);
			if(newComponent != null) {
				result.addHeader("Location", buildLocationHeader(newComponent));
			}
			
			return result;
			
		} else {
			
			return super.doPost(propertySet);
		}
	}

	@Override
	public RestMethodResult doDelete() throws FrameworkException {
		
		final Command deleteCommand = Services.command(securityContext, DeleteNodeCommand.class);
		final List<GraphObject> toDelete = doGet();
		final boolean cascade = false;
		
		for(GraphObject obj : toDelete) {

			if(obj instanceof Component) {

				Set<AbstractNode> contentNodes = new LinkedHashSet<AbstractNode>();
				contentNodes.addAll(((Component)obj).getContentNodes().values());
				
				for(AbstractNode contentNode : contentNodes) {
					deleteCommand.execute(contentNode, cascade);
				}
				
				deleteCommand.execute(obj, cascade);
			}

		}
		
		return new RestMethodResult(200);
	}

	
	@Override
	public Resource tryCombineWith(Resource next) throws FrameworkException {

		if (next instanceof UuidResource) {

			this.uuidResource = (UuidResource)next;
			return this;

		} else if (next instanceof DynamicTypeResource) {

			nestedResources.add((DynamicTypeResource)next);
			return this;
			
		} else if (next instanceof TypeResource) {

			throw new IllegalPathException();

		}

		return super.tryCombineWith(next);
	}
	
	@Override
	public boolean isCollectionResource() {
		
		if(uuidResource != null) {
			return uuidResource.isCollectionResource();
		}
		
		return true;
	}
	
	// ----- private methods -----
	private void copyRelationships(AbstractNode sourceNode, AbstractNode targetNode, String resourceId, String componentId, int position) throws FrameworkException {

		Command createRel = Services.command(securityContext, CreateRelationshipCommand.class);
		
		for(AbstractRelationship in : sourceNode.getIncomingRelationships()) {
			
			AbstractNode startNode   = in.getStartNode();
			RelationshipType relType = in.getRelType();
			
			AbstractRelationship newInRel = (AbstractRelationship)createRel.execute(startNode, targetNode, relType);
			newInRel.setProperty("type", in.getStringProperty("type"));
			
			// only set componentId if set
			if(componentId != null) {
				newInRel.setProperty(componentId, "*");
			}
			
			if(resourceId != null) {
				newInRel.setProperty(resourceId, position);
			}
		}
		
		for(AbstractRelationship out : sourceNode.getOutgoingRelationships()) {
			
			AbstractNode endNode     = out.getEndNode();
			RelationshipType relType = out.getRelType();
			
			AbstractRelationship newOutRel = (AbstractRelationship)createRel.execute(targetNode, endNode, relType);
			newOutRel.setProperty("type", out.getStringProperty("type"));

			if(componentId != null) {
				newOutRel.setProperty(componentId, "*");
			}
			
			if(resourceId != null) {
				newOutRel.setProperty(resourceId, position);
			}
		}
	}

	public String getResourceId() {
		return resourceId;
	}

	public void setResourceId(String resourceId) {
		this.resourceId = resourceId;
	}
}



























































