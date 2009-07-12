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
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import de.tuclausthal.submissioninterface.authfilter.SessionAdapter;
import de.tuclausthal.submissioninterface.executiontask.ExecutionTaskExecute;
import de.tuclausthal.submissioninterface.persistence.dao.DAOFactory;
import de.tuclausthal.submissioninterface.persistence.dao.ParticipationDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.SubmissionDAOIf;
import de.tuclausthal.submissioninterface.persistence.datamodel.Participation;
import de.tuclausthal.submissioninterface.persistence.datamodel.Submission;
import de.tuclausthal.submissioninterface.persistence.datamodel.Task;
import de.tuclausthal.submissioninterface.util.ContextAdapter;
import de.tuclausthal.submissioninterface.util.Util;

/**
 * Controller-Servlet for deleting a file in a submission
 * @author Sven Strickroth
 *
 */
public class DeleteFile extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		SubmissionDAOIf submissionDAO = DAOFactory.SubmissionDAOIf();
		Submission submission = submissionDAO.getSubmission(Util.parseInteger(request.getParameter("sid"), 0));
		if (submission == null) {
			request.setAttribute("title", "Abgabe nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		Task task = submission.getTask();

		// check Lecture Participation
		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf();
		Participation participation = participationDAO.getParticipation(new SessionAdapter(request).getUser(), task.getLecture());
		if (participation == null) {
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN, "insufficient rights");
			return;
		}

		Submission checkSubmission = submissionDAO.getSubmission(task, RequestAdapter.getUser(request));
		if (checkSubmission == null || submission.getSubmissionid() != checkSubmission.getSubmissionid()) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "insufficient rights");
			return;
		}

		if (task.getDeadline().before(new Date(new Date().getTime() - new Date().getTimezoneOffset() * 60 * 1000))) {
			request.setAttribute("title", "Abgabe nicht mehr m�glich");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		if (request.getPathInfo() == null) {
			request.setAttribute("title", "Ung�ltige Anfrage");
			request.getRequestDispatcher("MessageView").forward(request, response);

			return;
		}

		ContextAdapter contextAdapter = new ContextAdapter(getServletContext());
		File path = new File(contextAdapter.getDataPath().getAbsolutePath() + System.getProperty("file.separator") + task.getLecture().getId() + System.getProperty("file.separator") + task.getTaskid() + System.getProperty("file.separator") + submission.getSubmissionid() + System.getProperty("file.separator"));
		Boolean found = false;
		for (File file : path.listFiles()) {
			if (file.getName().equals(request.getPathInfo().substring(1))) {
				file.delete();
				found = true;
				break;
			}
		}
		if (found == true) {
			// delete .class files, might be useless now
			for (File file : path.listFiles()) {
				if (file.getName().endsWith(".class")) {
					file.delete();
				}
			}

			if (!submissionDAO.deleteIfNoFiles(submission, path)) {
				submission.setCompiles(null);
				submission.setTestResult(null);
				submissionDAO.saveSubmission(submission);
				ExecutionTaskExecute.compileTestTask(submission);
			}

			response.sendRedirect(response.encodeRedirectURL(request.getContextPath() + "/" + contextAdapter.getServletsPath() + "/ShowTask?taskid=" + task.getTaskid()));
			return;
		}

		request.setAttribute("title", "Datei nicht gefunden");
		request.getRequestDispatcher("MessageView").forward(request, response);
		return;
	}
}