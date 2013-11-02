package org.structr.web.entity.relation;

import org.structr.core.entity.relationship.AbstractChildren;
import org.structr.web.entity.Folder;

/**
 *
 * @author Christian Morgner
 */
public class Folders extends AbstractChildren<Folder, Folder> {

	@Override
	public Class<Folder> getSourceType() {
		return Folder.class;
	}

	@Override
	public Class<Folder> getTargetType() {
		return Folder.class;
	}
}