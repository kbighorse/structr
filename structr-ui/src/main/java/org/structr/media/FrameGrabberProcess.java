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
package org.structr.media;

import org.structr.util.AbstractProcess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.graph.Tx;
import org.structr.web.common.FileHelper;
import org.structr.web.entity.Image;
import org.structr.web.entity.VideoFile;

/**
 * A video converter process that calls a predefined script with a given
 * set of parameters. The script(s) can be declared in the structr.conf
 * configuration file. Each script represents a single conversion
 * process. The script must take two parameters: the input file name
 * on disk and the output file name on disk, e.g.
 *
 * transcode.sh [input] [output]
 *
 *
 */

public class FrameGrabberProcess extends AbstractProcess<Image> {

	private static final Logger logger = Logger.getLogger(FrameGrabberProcess.class.getName());

	private Image newFile         = null;
	private VideoFile inputFile   = null;
	private String outputFileName = null;
	private String imageName      = null;
	private String scriptName     = null;
	private String fileExtension  = null;
	private long timeIndex        = -1;

	public FrameGrabberProcess(final SecurityContext securityContext, final VideoFile inputFile, final String imageName, final long timeIndex, final String scriptName) {

		super(securityContext);

		this.inputFile     = inputFile;
		this.scriptName    = scriptName;
		this.fileExtension = ".tmp-" + System.currentTimeMillis();
		this.timeIndex     = timeIndex;
		this.imageName     = imageName;
	}

	@Override
	public void preprocess() {

		try (final Tx tx = StructrApp.getInstance(securityContext).tx()) {

			// create an empty file to store the converted video
			newFile = FileHelper.createFile(securityContext, new byte[0], null, Image.class, imageName);

			// obtain destination path of new file
			outputFileName = newFile.getFileOnDisk().getAbsolutePath();

			tx.success();

		} catch (FrameworkException | IOException fex) {
			logger.log(Level.WARNING, "", fex);
		}
	}

	@Override
	public StringBuilder getCommandLine() {

		final String scriptNameFromConfig = StructrApp.getConfigurationValue("VideoFile." + scriptName);
		if (scriptNameFromConfig != null) {

			final StringBuilder commandLine = new StringBuilder(scriptNameFromConfig);

			// build command line from builder options
			commandLine.append(" ");
			commandLine.append(inputFile.getDiskFilePath(securityContext));
			commandLine.append(" ");
			commandLine.append(outputFileName);
			commandLine.append(fileExtension);
			commandLine.append(" ");
			commandLine.append(timeIndex);

			return commandLine;

		} else {

			logger.log(Level.WARNING, "No VideoFile.{0} registered in structr.conf.", scriptName);
		}

		return null;
	}

	@Override
	public Image processExited(int exitCode) {

		final App app = StructrApp.getInstance(securityContext);

		if (exitCode == 0) {

			try (final Tx tx = app.tx()) {

				// move converted file into place
				final java.io.File diskFile = new java.io.File(outputFileName + fileExtension);
				final java.io.File dstFile  = new java.io.File(outputFileName);
				if (diskFile.exists()) {

					Files.move(diskFile.toPath(), dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					FileHelper.updateMetadata(newFile);

					// create link between the two videos
					inputFile.setProperty(VideoFile.posterImage, newFile);
				}

				tx.success();

			} catch (FrameworkException | IOException fex) {
				logger.log(Level.WARNING, "", fex);
			}

		} else {

			// delete file, conversion has failed
			try (final Tx tx = app.tx()) {

				app.delete(newFile);
				tx.success();

			} catch (FrameworkException fex) {
				logger.log(Level.WARNING, "", fex);
			}

		}

		return newFile;
	}
}
