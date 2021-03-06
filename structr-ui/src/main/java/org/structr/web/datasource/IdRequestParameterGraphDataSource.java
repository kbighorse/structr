/**
 * Copyright (C) 2010-2016 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.web.datasource;

import java.util.LinkedList;
import java.util.List;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.app.StructrApp;
import org.structr.core.entity.AbstractNode;
import org.structr.web.common.GraphDataSource;
import org.structr.web.common.RenderContext;

/**
 *
 *
 */
public class IdRequestParameterGraphDataSource implements GraphDataSource<List<GraphObject>> {

	private String parameterName = null;

	public IdRequestParameterGraphDataSource(String parameterName) {
		this.parameterName = parameterName;
	}

	@Override
	public List<GraphObject> getData(final RenderContext renderContext, final AbstractNode referenceNode) throws FrameworkException {

		final SecurityContext securityContext = renderContext.getSecurityContext();
		if (securityContext != null && securityContext.getRequest() != null) {

			String nodeId = securityContext.getRequest().getParameter(parameterName);
			if (nodeId != null) {

				AbstractNode node = (AbstractNode) StructrApp.getInstance(securityContext).getNodeById(nodeId);
				if (node != null) {

					List<GraphObject> graphData = new LinkedList<>();
					graphData.add(node);

					return graphData;
				}
			}
		}

		return null;
	}
}
