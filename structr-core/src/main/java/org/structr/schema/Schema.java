package org.structr.schema;

import java.util.Set;
import org.neo4j.graphdb.PropertyContainer;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;

/**
 *
 * @author Christian Morgner
 */
public interface Schema {
	
	public String getSource(final ErrorBuffer errorBuffer) throws FrameworkException;
	public PropertyContainer getPropertyContainer();
	public Set<String> getViews();
	public String getClassName();
}