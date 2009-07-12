/*
 * Copyright 2009 Sven Strickroth <email@cs-ware.de>
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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import de.tuclausthal.submissioninterface.authfilter.SessionAdapter;
import de.tuclausthal.submissioninterface.executiontask.ExecutionTaskExecute;
import de.tuclausthal.submissioninterface.persistence.dao.DAOFactory;
import de.tuclausthal.submissioninterface.persistence.dao.ParticipationDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.SubmissionDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.TaskDAOIf;
import de.tuclausthal.submissioninterface.persistence.datamodel.Participation;
import de.tuclausthal.submissioninterface.persistence.datamodel.ParticipationRole;
import de.tuclausthal.submissioninterface.persistence.datamodel.Submission;
import de.tuclausthal.submissioninterface.persistence.datamodel.Task;
import de.tuclausthal.submissioninterface.template.Template;
import de.tuclausthal.submissioninterface.template.TemplateFactory;
import de.tuclausthal.submissioninterface.util.ContextAdapter;
import de.tuclausthal.submissioninterface.util.Util;

/**
 * Controller-Servlet for the submission of files
 * @author Sven Strickroth
 */
public class SubmitSolution extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		TaskDAOIf taskDAO = DAOFactory.TaskDAOIf();
		Task task = taskDAO.getTask(Util.parseInteger(request.getParameter("taskid"), 0));
		if (task == null) {
			request.setAttribute("title", "Aufgabe nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		// check Lecture Participation
		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf();
		Participation participation = participationDAO.getParticipation(new SessionAdapter(request).getUser(), task.getLecture());
		if (participation == null) {
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, "insufficient rights");
			return;
		}

		if (task.getStart().after(new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60*1000)) && participation.getRoleType().compareTo(ParticipationRole.TUTOR) < 0) {
			request.setAttribute("title", "Abgabe nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		if (task.getDeadline().before(new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60*1000)) || participation.getRoleType().compareTo(ParticipationRole.TUTOR) >= 0) {
			request.setAttribute("title", "Abgabe nicht mehr m�glich");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		request.setAttribute("task", task);
		request.getRequestDispatcher("SubmitSolutionFormView").forward(request, response);
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Template template = TemplateFactory.getTemplate(request, response);

		PrintWriter out = response.getWriter();

		TaskDAOIf taskDAO = DAOFactory.TaskDAOIf();
		Task task = taskDAO.getTask(Util.parseInteger(request.getParameter("taskid"), 0));
		if (task == null) {
			template.printTemplateHeader("Aufgabe nicht gefunden");
			out.println("<div class=mid><a href=\"" + response.encodeURL("?") + "\">zur �bersicht</a></div>");
			template.printTemplateFooter();
			return;
		}

		// check Lecture Participation
		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf();
		Participation participation = participationDAO.getParticipation(new SessionAdapter(request).getUser(), task.getLecture());
		if (participation == null) {
			template.printTemplateHeader("Ung�ltige Anfrage");
			out.println("<div class=mid>Sie sind kein Teilnehmer dieser Veranstaltung.</div>");
			out.println("<div class=mid><a href=\"" + response.encodeURL("Overview") + "\">zur �bersicht</a></div>");
			template.printTemplateFooter();
			return;
		}

		if (task.getStart().after(new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60*1000)) && participation.getRoleType().compareTo(ParticipationRole.TUTOR) < 0) {
			template.printTemplateHeader("Aufgabe nicht abrufbar");
			out.println("<div class=mid><a href=\"" + response.encodeURL("?") + "\">zur �bersicht</a></div>");
			template.printTemplateFooter();
			return;
		}

		if (task.getDeadline().before(new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60*1000)) || participation.getRoleType().compareTo(ParticipationRole.TUTOR) >= 0) {
			template.printTemplateHeader("Abgabe nicht (mehr) m�glich");
			out.println("<div class=mid><a href=\"" + response.encodeURL("?") + "\">zur �bersicht</a></div>");
			template.printTemplateFooter();
			return;
		}

		//mainbetternamereq.printTemplateHeader("Upload");

		//http://commons.apache.org/fileupload/using.html

		// Check that we have a file upload request
		boolean isMultipart = ServletFileUpload.isMultipartContent(request);

		if (isMultipart) {

			// Create a factory for disk-based file items
			FileItemFactory factory = new DiskFileItemFactory();

			// Set factory constraints
			//factory.setSizeThreshold(yourMaxMemorySize);
			//factory.setRepository(yourTempDirectory);

			// Create a new file upload handler
			ServletFileUpload upload = new ServletFileUpload(factory);

			// Parse the request
			List<FileItem> items = null;
			try {
				items = upload.parseRequest(request);
			} catch (FileUploadException e) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST, "filename invalid");
				return;
			}

			SubmissionDAOIf submissionDAO = DAOFactory.SubmissionDAOIf();
			Submission submission = submissionDAO.createSubmission(task, participation);

			ContextAdapter contextAdapter = new ContextAdapter(getServletContext());

			File path = new File(contextAdapter.getDataPath().getAbsolutePath() + System.getProperty("file.separator") + task.getLecture().getId() + System.getProperty("file.separator") + task.getTaskid() + System.getProperty("file.separator") + submission.getSubmissionid() + System.getProperty("file.separator"));
			if (path.exists() == false) {
				path.mkdirs();
			}
			// Process the uploaded items
			Iterator<FileItem> iter = items.iterator();
			while (iter.hasNext()) {
				FileItem item = iter.next();

				// Process a file upload
				if (!item.isFormField()) {
					Pattern pattern = Pattern.compile("(\\|/)?([A-Z][A-Za-z0-9_]+\\.java)$");
					Matcher m = pattern.matcher(item.getName());
					if (!m.matches()) {
						out.println("Filename invalid ;)");
						return;
					}
					String fileName = m.group(0);
					File uploadedFile = new File(path, fileName);
					try {
						item.write(uploadedFile);
					} catch (Exception e) {
						e.printStackTrace();
					}

					submission.setCompiles(null);
					submission.setTestResult(null);
					submissionDAO.saveSubmission(submission);
					ExecutionTaskExecute.compileTestTask(submission);

					response.sendRedirect(response.encodeRedirectURL("ShowTask?taskid=" + task.getTaskid()));
				}
			}
		} else {
			out.println("fuck111" + isMultipart);
		}
	}
}
