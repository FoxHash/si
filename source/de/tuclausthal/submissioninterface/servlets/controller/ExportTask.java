/*
 * Copyright 2012-2014 Sven Strickroth <email@cs-ware.de>
 * 
 * This file is part of the SubmissionInterface.
 * 
 * SubmissionInterface is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 * 
 * SubmissionInterface is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SubmissionInterface. If not, see <http://www.gnu.org/licenses/>.
 */

package de.tuclausthal.submissioninterface.servlets.controller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.hibernate.Session;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.tuclausthal.submissioninterface.persistence.dao.DAOFactory;
import de.tuclausthal.submissioninterface.persistence.dao.ParticipationDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.TaskDAOIf;
import de.tuclausthal.submissioninterface.persistence.datamodel.CompileTest;
import de.tuclausthal.submissioninterface.persistence.datamodel.JUnitTest;
import de.tuclausthal.submissioninterface.persistence.datamodel.Participation;
import de.tuclausthal.submissioninterface.persistence.datamodel.ParticipationRole;
import de.tuclausthal.submissioninterface.persistence.datamodel.PointCategory;
import de.tuclausthal.submissioninterface.persistence.datamodel.Task;
import de.tuclausthal.submissioninterface.persistence.datamodel.Test;
import de.tuclausthal.submissioninterface.servlets.RequestAdapter;
import de.tuclausthal.submissioninterface.util.ContextAdapter;
import de.tuclausthal.submissioninterface.util.Util;

/**
 * Controller-Servlet for exporting tasks by advisors
 * @author Sven Strickroth
 */
public class ExportTask extends HttpServlet {
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Session session = RequestAdapter.getSession(request);

		TaskDAOIf taskDAO = DAOFactory.TaskDAOIf(session);
		Task task = taskDAO.getTask(Util.parseInteger(request.getParameter("taskid"), 0));
		if (task == null) {
			request.setAttribute("title", "Aufgabe nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf(session);
		Participation participation = participationDAO.getParticipation(RequestAdapter.getUser(request), task.getTaskGroup().getLecture());
		if (participation == null || participation.getRoleType() != ParticipationRole.ADVISOR) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "insufficient rights");
			return;
		}

		if (task.isADynamicTask()) {
			request.setAttribute("title", "Export of dynamic tasks not possible");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		// use a tmp file here to be able to output error messages until we send the zip file
		File tmpFile = File.createTempFile("export", null);
		try {
			ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFile)));

			DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
			Document doc = docBuilder.newDocument();

			Element root = doc.createElement("task");
			root.setAttribute("xmlns", "urn:proforma:task:v1.0.1");
			root.setAttribute("xmlns:unit", "urn:proforma:tests:unittest:v1");
			root.setAttribute("xmlns:gate", "urn:proforma:gate");
			root.setAttribute("uuid", task.getUUID());
			root.setAttribute("parent-uuid", task.getParentUUID());
			root.setAttribute("lang", "de");
			doc.appendChild(root);

			Element description = doc.createElement("description");
			description.setTextContent(task.getDescription());
			root.appendChild(description);

			Element proglang = doc.createElement("proglang");
			proglang.setAttribute("version", "1.7");
			proglang.setTextContent("java");
			root.appendChild(proglang);

			Element submissionRestrictions = doc.createElement("submission-restrictions");
			root.appendChild(submissionRestrictions);
			
			//Export Regexp Restrictions/File Restrictions
			if (!task.getFilenameRegexp().endsWith(".jar") && !task.getFilenameRegexp().endsWith(".zip") && task.getArchiveFilenameRegexp().equals("-")) {
			    //File Restrictions
		        if (task.getFilenameRegexp().startsWith("^")) {
				    Element filesRestriction = doc.createElement("files-restriction");
				    submissionRestrictions.appendChild(filesRestriction);
					Element required = doc.createElement("required");
				    required.setAttribute("filename", task.getFilenameRegexp().substring(1));
				    required.setAttribute("max-size", "" + task.getMaxsize());
				    filesRestriction.appendChild(required);
				//Regexp Restrictions
				} else {
				    Element regexpRestriction = doc.createElement("regexp-restriction");
				    regexpRestriction.setAttribute("max-size", "" + task.getMaxsize());
				    if (task.getFilenameRegexp().equals("")) {
				    	regexpRestriction.setTextContent("[A-Za-z0-9. _-]+");
				    } else {	
				    	regexpRestriction.setTextContent(task.getFilenameRegexp());
				    }
				    submissionRestrictions.appendChild(regexpRestriction);
				}
			
			//Export Archive Restrictions
			} else {
				Element archiveRestriction = doc.createElement("archive-restriction");
			    submissionRestrictions.appendChild(archiveRestriction);
			    archiveRestriction.setAttribute("max-size", "" + task.getMaxsize());
			    
			    if (task.getFilenameRegexp() != "") {
			        archiveRestriction.setAttribute("allowed-archive-filename", task.getFilenameRegexp());
			    } else {
			    	archiveRestriction.setAttribute("allowed-archive-filename", "[A-Za-z0-9. _-]+");
			    }
			    
			    if (task.getArchiveFilenameRegexp().equals("-")) {
			        archiveRestriction.setAttribute("unpack-files-from-archive", "false");
			    } else {
			    	archiveRestriction.setAttribute("unpack-files-from-archive", "true");
			        if (task.getArchiveFilenameRegexp().startsWith("^")) {
			            Element fileRestrictions = doc.createElement("file-restrictions");
			            archiveRestriction.appendChild(fileRestrictions);
			            Element required = doc.createElement("required");
			            fileRestrictions.appendChild(required);
			            required.setAttribute("path", task.getArchiveFilenameRegexp().substring(1));  
			         } else {
			        	Element unpackFilesFromArchiveRegexp = doc.createElement("unpack-files-from-archive-regexp");
			        	if (task.getArchiveFilenameRegexp() != "") {
			        	    unpackFilesFromArchiveRegexp.setTextContent(task.getArchiveFilenameRegexp());
			        	} else {
			        		unpackFilesFromArchiveRegexp.setTextContent("[A-Za-z0-9. _-]+");
			        	}
			        	archiveRestriction.appendChild(unpackFilesFromArchiveRegexp);   	
			         }
			     }
            }
			
			File taskPath = new File(new ContextAdapter(getServletContext()).getDataPath().getAbsolutePath() + System.getProperty("file.separator") + task.getTaskGroup().getLecture().getId() + System.getProperty("file.separator") + task.getTaskid());
			File advisorFilesPath = new File(taskPath, "advisorfiles");
			Element files = doc.createElement("files");
			root.appendChild(files);
			FilesBean filesBean = new FilesBean(files);
			for (String advisorFile : Util.listFilesAsRelativeStringList(advisorFilesPath)) {
				Element file = doc.createElement("file");
				file.setAttribute("id", (++filesBean.fileId).toString());
				file.setAttribute("filename", advisorFile);
				file.setAttribute("comment", "");
				if (advisorFile.endsWith(".java")) { // TODO: HACK
					file.setAttribute("class", "template");
				} else {
					file.setAttribute("class", "instruction");
				}
				File referencedFile = new File(advisorFilesPath, advisorFile);
				// embed a file only if it's a known plain text file and ASCII or UTF-8
				if (ShowFile.isPlainTextFile(advisorFile.toLowerCase()) && ShowFile.isUTF8(referencedFile)) {
					file.setTextContent(Util.loadFile(referencedFile, true).toString());
					file.setAttribute("type", "embedded");
				} else {
					file.setAttribute("type", "file");
					String fileNameInZip = "advisorfile/" + advisorFile;
					file.setTextContent(fileNameInZip);
					zipOut.putNextEntry(new ZipEntry(fileNameInZip));
					Util.copyInputStreamAndClose(new FileInputStream(new File(advisorFilesPath, advisorFile)), zipOut);
				}
				files.appendChild(file);
			}

			Element modelSolutions = doc.createElement("model-solutions");
			root.appendChild(modelSolutions);
			Integer modelSolutionId = 0;
			File modelSolutionFilesPath = new File(taskPath, "modelsolutionfiles");
			for (String modelSolutionFile : Util.listFilesAsRelativeStringList(modelSolutionFilesPath)) {
				Element modelSolution = doc.createElement("model-solution");
				modelSolution.setAttribute("id", (++modelSolutionId).toString());
				modelSolution.setAttribute("comment", "");
				modelSolutions.appendChild(modelSolution);
				Element modelSolutionFileRefs = doc.createElement("filerefs");
				modelSolution.appendChild(modelSolutionFileRefs);
				
				File referencedFile = new File(modelSolutionFilesPath, modelSolutionFile);
				Element file = doc.createElement("file");
				file.setAttribute("id", (++filesBean.fileId).toString());
				file.setAttribute("filename", referencedFile.getName());
				file.setAttribute("class", "internal");
				//embed a file only if it's a known plain text file and ASCII or UTF-8
				if (ShowFile.isPlainTextFile(modelSolutionFile.toLowerCase()) && ShowFile.isUTF8(referencedFile)) {
					file.setTextContent(Util.loadFile(referencedFile, true).toString());
					file.setAttribute("type", "embedded");
				} else {
					file.setAttribute("type", "file");
					String fileNameInZip = "modelsolution/" + modelSolutionFile; // to make sure the filename is unique!
					file.setTextContent(fileNameInZip);
					zipOut.putNextEntry(new ZipEntry(fileNameInZip));
					Util.copyInputStreamAndClose(new FileInputStream(referencedFile), zipOut);
				}
				files.appendChild(file);
				Element modelSolutionFileRef = doc.createElement("fileref");
				modelSolutionFileRef.setAttribute("refid", filesBean.fileId.toString());
				modelSolutionFileRefs.appendChild(modelSolutionFileRef);
			}

			Element tests = doc.createElement("tests");
			root.appendChild(tests);
			if (!task.getTests().isEmpty()) {
				Integer testId = 0;
				for (Test test : task.getTests()) {
					Element testElement = doc.createElement("test");
					testElement.setAttribute("id", (++testId).toString());
					Element testTitle = doc.createElement("title");
					testTitle.setTextContent(test.getTestTitle());
					testElement.appendChild(testTitle);
					Element testType = doc.createElement("test-type");
					testElement.appendChild(testType);
					Element testConfiguration = doc.createElement("test-configuration");
					testElement.appendChild(testConfiguration);

					if (test instanceof JUnitTest) {
						JUnitTest juTest = (JUnitTest) test;

						testType.setTextContent("unittest");
						Element testFileRefs = doc.createElement("filerefs");
						testConfiguration.appendChild(testFileRefs);
						Element unittest = doc.createElementNS("urn:proforma:tests:unittest:v1", "unit:unittest");
						unittest.setAttribute("framework", "JUnit");
						unittest.setAttribute("version", "3");
						testConfiguration.appendChild(unittest);
						Element mainClass = doc.createElementNS("urn:proforma:tests:unittest:v1", "unit:main-class");
						mainClass.setTextContent(juTest.getMainClass());
						unittest.appendChild(mainClass);
						Element timeOut = doc.createElementNS("urn:proforma:tests:unittest:v1", "unit:timeout");
						timeOut.setTextContent(((Integer) test.getTimeout()).toString());
						unittest.appendChild(timeOut);

						// fill files and filerefs
						File testJarFile = new File(taskPath, "junittest" + test.getId() + ".jar");
						if (!testJarFile.exists() && !testJarFile.isFile())
							continue;

						extractJavaFilesFromJarFile(test, testJarFile, zipOut, doc, filesBean, testFileRefs);
					} else if (test instanceof CompileTest) {
						testType.setTextContent("java-compilation");
					} else {
						testType.setTextContent("gate:" + test.getClass().getName());
					}

					Element testMetaData = doc.createElement("test-meta-data");
					testConfiguration.appendChild(testMetaData);
					//Export GATE test meta-data
					Element testDescription = doc.createElementNS("urn:proforma:gate", "gate:test-description");
					testDescription.setTextContent(test.getTestDescription());
					testMetaData.appendChild(testDescription);
					Element runnableByStudents = doc.createElementNS("urn:proforma:gate", "gate:times-runnable-by-students");
					runnableByStudents.setTextContent(((Integer) test.getTimesRunnableByStudents()).toString());
					if (test.getTimesRunnableByStudents() > 0) {
						runnableByStudents.setAttribute("show-details-to-students", test.isGiveDetailsToStudents() ? "true" : "false");
					}
					testMetaData.appendChild(runnableByStudents);
					Element isTutorTest = doc.createElementNS("urn:proforma:gate", "gate:tutor-test");
					isTutorTest.setTextContent(test.isForTutors() ? "true" : "false");
					testMetaData.appendChild(isTutorTest);

					tests.appendChild(testElement);
				}
			}

			//Export GATE grading-hints
			Element gradingHints = doc.createElement("grading-hints");
			root.appendChild(gradingHints);
			Element minPointStep = doc.createElementNS("urn:proforma:gate", "gate:min-point-step");
			minPointStep.setTextContent("" + task.getMinPointStep());
			gradingHints.appendChild(minPointStep);
			if (task.getPointCategories() == null || task.getPointCategories().size() == 0) {
				Element maxPoints = doc.createElementNS("urn:proforma:gate", "gate:max-points");
				maxPoints.setTextContent("" + task.getMaxPoints());
				gradingHints.appendChild(maxPoints);
			} else {
				for (PointCategory category : task.getPointCategories()) {
					Element pointCategory = doc.createElementNS("urn:proforma:gate", "gate:pointy-category");
					pointCategory.setTextContent(category.getDescription());
					pointCategory.setAttribute("optional", category.isOptional() ? "true" : "false");
					pointCategory.setAttribute("max-points", "" + category.getPoints());
					gradingHints.appendChild(pointCategory);
				}
			}
			
			Element metaData = doc.createElement("meta-data");
			root.appendChild(metaData);
			Element taskTitle = doc.createElement("title");
			taskTitle.setTextContent(task.getTitle());
			metaData.appendChild(taskTitle);

			TransformerFactory transfac = TransformerFactory.newInstance();
			Transformer trans = transfac.newTransformer();
			// trans.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			trans.setOutputProperty(OutputKeys.INDENT, "yes");

			zipOut.putNextEntry(new ZipEntry("task.xml"));
			StreamResult result = new StreamResult(new OutputStreamWriter(zipOut));
			DOMSource source = new DOMSource(doc);
			trans.transform(source, result);
			zipOut.closeEntry();

			response.setContentType("application/zip");
			response.setHeader("Content-Disposition", "attachment; filename=\"export-task" + task.getTaskid() + ".zip\"");
			zipOut.close();
			Util.copyInputStreamAndClose(new BufferedInputStream(new FileInputStream(tmpFile)), response.getOutputStream());
		} catch (Exception e) {
		} finally {
			tmpFile.delete();
		}
	}

	private void extractJavaFilesFromJarFile(Test test, File testJarFile, ZipOutputStream zipOut, Document doc, FilesBean files, Element fileRefs) throws IOException {
		File tempDir = Util.createTemporaryDirectory("jar", null);
		ZipInputStream zipFile = new ZipInputStream(new FileInputStream(testJarFile));
		ZipEntry entry = null;
		while ((entry = zipFile.getNextEntry()) != null) {
			if (entry.isDirectory()) {
				continue;
			}
			if (!entry.getName().endsWith(".java")) {
				System.err.println("Ignored entry: " + entry.getName());
				continue;
			}
			if (!ImportTask.isPathSafe(tempDir, entry.getName())) {
				System.err.println("Ignored entry: " + entry.getName() + "; tries to get out of tempDir");
				continue;
			}

			File fileToCreate = new File(tempDir, entry.getName());
			if (!fileToCreate.getParentFile().exists()) {
				fileToCreate.getParentFile().mkdirs();
			}
			Util.copyInputStreamAndClose(zipFile, new BufferedOutputStream(new FileOutputStream(fileToCreate)));
		}
		zipFile.close();
		
		for (String testFile : Util.listFilesAsRelativeStringList(tempDir)) {
			File referencedFile = new File(tempDir, testFile);

			Element file = doc.createElement("file");
			file.setAttribute("id", (++files.fileId).toString());
			file.setAttribute("filename", referencedFile.getName());
			file.setAttribute("class", "internal");
			// embed a file only if it's a known plain text file and ASCII or UTF-8
			if (ShowFile.isPlainTextFile(referencedFile.getName().toLowerCase()) && ShowFile.isUTF8(referencedFile)) {
				file.setTextContent(Util.loadFile(referencedFile, true).toString());
				file.setAttribute("type", "embedded");
			} else {
				file.setAttribute("type", "file");
				String fileNameInZip = "test" + test.getId() + "/" + referencedFile.getName();
				file.setTextContent(fileNameInZip);
				zipOut.putNextEntry(new ZipEntry(fileNameInZip));
				Util.copyInputStreamAndClose(new FileInputStream(referencedFile), zipOut);
			}
			files.files.appendChild(file);
			Element fileRef = doc.createElement("fileref");
			fileRef.setAttribute("refid", files.fileId.toString());
			fileRefs.appendChild(fileRef);
		}
	}

	private static class FilesBean {
		FilesBean(Element files) {
			this.files = files;
		}

		Integer fileId = 0;
		final Element files;
	}
}
