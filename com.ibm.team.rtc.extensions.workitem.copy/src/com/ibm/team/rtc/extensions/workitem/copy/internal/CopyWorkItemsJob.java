/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Sandeep Somavarapu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.ibm.team.rtc.extensions.workitem.copy.internal;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.naming.AuthenticationException;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.json.JSONException;
import org.json.JSONObject;

import com.ibm.team.foundation.common.text.XMLString;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.registry.IEndPointDescriptor;
import com.ibm.team.repository.common.Location;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.rtc.extensions.workitem.copy.WorkItemsCopyPlugIn;
import com.ibm.team.rtc.extensions.workitem.copy.internal.WorkItemsResolver.IWorkItems;
import com.ibm.team.rtc.extensions.workitem.copy.link.processors.ILinkProcessor;
import com.ibm.team.rtc.extensions.workitem.copy.link.processors.LinkProcessors;
import com.ibm.team.rtc.extensions.workitem.copy.value.processors.IValueProcessor;
import com.ibm.team.rtc.extensions.workitem.copy.value.processors.ValueProcessors;
import com.ibm.team.rtc.extensions.workitem.copy.value.processors.WorkItemTypeProcessor;
import com.ibm.team.workitem.client.WorkItemWorkingCopy;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.IComment;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemReferences;
import com.ibm.team.workitem.common.model.IWorkItemType;
import com.ibm.team.workitem.common.model.WorkItemEndPoints;
import com.ibm.team.workitem.common.text.WorkItemTextUtilities;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.Base64;

public class CopyWorkItemsJob {

	private final EvaluationContext fContext;

	public CopyWorkItemsJob(EvaluationContext context) {
		fContext= context;
	}

	public void run(IProgressMonitor monitor) {
		System.out.println("IN CopyWorkItemsJob.run()");
		SubMonitor progress= SubMonitor.convert(monitor);
		List<IWorkItemHandle> allSources= new ArrayList<IWorkItemHandle>();
		List<IWorkItemHandle> allTargets= new ArrayList<IWorkItemHandle>();
		try {
			WorkItemsResolver workItemsResolver= new WorkItemsResolver(fContext);
			IWorkItems workItems= workItemsResolver.resolve(progress.newChild(10));
			int totalResultsSize= workItemsResolver.getTotalResultsSize();
			TargetAttributes targetAttributes= new TargetAttributes(fContext);

			progress.beginTask("Copy Work Items", (totalResultsSize * 5) + 1);

			// Step 1: Create targets
			List<WorkItemWorkingCopy> targets = createTargets(workItems, targetAttributes, allSources, allTargets,
					totalResultsSize, progress.newChild(totalResultsSize));
			
			// Step2: Prepare attributes for save
			// prepareAttributesForTargets(targets, targetAttributes,
			// progress.newChild(totalResultsSize + 1));

			// Step 3: Copy Work Items
			// copyTargets(targets, "Copying Work Items",
			// progress.newChild(totalResultsSize));

			// if (fContext.configuration.copyLinks ||
			// fContext.configuration.copyAttachments) {
				// Step 4: Copy links and attachments
			// copyLinks(targets, progress.newChild(totalResultsSize * 2));
			// }
			// fContext.status= Status.OK_STATUS;
		} catch (TeamRepositoryException e) {
			fContext.status= new Status(IStatus.ERROR, WorkItemsCopyPlugIn.ID, e.getMessage(), e);
		} finally {
			fContext.result= allTargets;
			for (IWorkItemHandle source : allSources) {
				fContext.sourceContext.workingCopyManager.disconnect(source);
			}
			fContext.sourceContext.workingCopyManager.dispose();
			for (IWorkItemHandle workItem : allTargets) {
				fContext.targetContext.workingCopyManager.disconnect(workItem);
			}
			fContext.targetContext.workingCopyManager.dispose();
		}
		progress.done();
	}

	private List<WorkItemWorkingCopy> createTargets(IWorkItems workItems, TargetAttributes targetAttributes, List<IWorkItemHandle> allSources, List<IWorkItemHandle> allTargets, int totalResultsSize, SubMonitor creationMonitor) throws TeamRepositoryException {
		List<WorkItemWorkingCopy> targets= new ArrayList<WorkItemWorkingCopy>();
		int counter= 1;
		while (workItems.hasNext()) {
			Collection<IWorkItem> batch= workItems.next();
			System.out.println(batch);
			for (IWorkItem source : batch) {
				String str = source.getWorkItemType();
				try {
					String str1 = new JSONObject().put("issue type:", str).toString();
					System.out.println(str1);
				} catch (JSONException e) { // TODO Auto-generated catch block
					e.printStackTrace();
				}

				System.out.println(source.getWorkItemType());
				// String str = source.getWorkItemType();

				if (str.contains("defect")) {
					str = "Bug";
				}

				if (str.contains("subtask")) {
					str = "Sub-Task";

				}

				if (str.contains("impediment")) {
					str = "Task";

				}
				if (str.contains("story")) {
					str = "story";
				}
				if (str.contains("epic")) {
					str = "epic";
				}



				/*
				 * String timestamp1 = source.getCreationDate().toString();
				 * System.out.println("extracted timestamp is :" + timestamp1);
				 * 
				 * String createdatetransform = datetransform(timestamp1);
				 * System.out.println("created date after transform:" +
				 * createdatetransform); System.out.println("after:" + str);
				 */
				
				String baseurl = "https://gbsjiratest.in.edst.ibm.com";
				String auth = new String(Base64.encode("libertydevlocal:libertydevlocal"));
				// Create a trust manager that does not validate certificate
				// chains.
				// without this when you execute you get clienthandlerexception
				TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
					public X509Certificate[] getAcceptedIssuers() {
						return null;
					}

					public void checkClientTrusted(X509Certificate[] certs, String authType) {
					}

					public void checkServerTrusted(X509Certificate[] certs, String authType) {
					}
				} };

				// Install the all-trusting trust manager
				try {
					SSLContext sc = SSLContext.getInstance("TLS");
					sc.init(null, trustAllCerts, new SecureRandom());
					HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
				} catch (Exception e) {
					;
				}
				try {
					// Create Issue
					String createIssueData = "{\"fields\":{\"project\":{\"key\":\"HELLHE5\"},\"summary\":\"REST Test\",\"issuetype\":{\"name\":\"Bug\"}}}";
					String issue = invokePostMethod(auth, baseurl + "/rest/api/2/issue", createIssueData);
					System.out.println(issue);
				} catch (AuthenticationException e1) {
					System.out.println("Username or Password wrong!");
					e1.printStackTrace();
				} catch (ClientHandlerException e2) {
					System.out.println("Error invoking REST method");
					e2.printStackTrace();
				}

				SubMonitor singleMonitor= creationMonitor.newChild(1);
				singleMonitor.setTaskName("Creating Work Item " + "(" + counter + " of " + totalResultsSize + ")");
				String targetType= new WorkItemTypeProcessor().getMapping(null, targetAttributes.findAttribute(IWorkItem.TYPE_PROPERTY, singleMonitor), source.getWorkItemType(), fContext, singleMonitor);
				if (targetType == null) {
					throw new TeamRepositoryException("Mapping not found for work item type: " + source.getWorkItemType());
				}
				WorkItemWorkingCopy target= newTarget(targetType, singleMonitor);
				
				fContext.sourceContext.addPair(source, target.getWorkItem());
				fContext.targetContext.addPair(target.getWorkItem(), source);
				
				targets.add(target);
				allSources.add(source);
				allTargets.add(target.getWorkItem());
				
				singleMonitor.done();
				counter++;
			}

		}
		creationMonitor.done();
		return targets;
	}

	/*
	 * String datetransform(String date) { try { SimpleDateFormat
	 * simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:s.S",
	 * Locale.ENGLISH); Date date1 = simpleDateFormat.parse(date);
	 * 
	 * System.out.println(" in datetransform function date as is:" + date);
	 * System.out.println(" in datetransform function- after parse -date1 :" +
	 * date1.toString());
	 * 
	 * SimpleDateFormat simpleDateFormat1 = new SimpleDateFormat("dd/MMM/yy'
	 * 'h:mm a"); String date2 = simpleDateFormat1.format(date1);
	 * System.out.println("after transform:" + date2);
	 * 
	 * // Timestamp timeStampDate = new Timestamp(date3.getTime()); return
	 * date2;
	 * 
	 * } catch (ParseException e) { System.out.println("Exception :" + e);
	 * return null; } }
	 */

	public static String invokePostMethod(String auth, String url, String data)
			throws AuthenticationException, ClientHandlerException {
		Client client = Client.create();
		WebResource webResource = client.resource(url);
		ClientResponse response = webResource.header("Authorization", "Basic " + auth).type("application/json")
				.accept("application/json").post(ClientResponse.class, data);
		int statusCode = response.getStatus();
		if (statusCode == 401) {
			throw new AuthenticationException("Invalid Username or Password");
		}
		return response.getEntity(String.class);
	}

	private List<WorkItemWorkingCopy> prepareAttributesForTargets(List<WorkItemWorkingCopy> workingCopies, TargetAttributes targetAttributes, SubMonitor progress) throws TeamRepositoryException {
		Collection<IAttribute> sourceAttributes= new SourceAttributes(fContext).get(progress.newChild(1));
		BatchIterator<WorkItemWorkingCopy> iterator= new BatchIterator<WorkItemWorkingCopy>(workingCopies, WorkItemsResolver.BATCH_SIZE);
		int counter= 1;
		while (iterator.hasNext()) {
			Collection<WorkItemWorkingCopy> batch= iterator.next();
			SubMonitor preparingMonitor= progress.newChild(batch.size());
			
			for (WorkItemWorkingCopy target : batch) {
				SubMonitor singleMonitor= preparingMonitor.newChild(1);
				singleMonitor.setTaskName("Preparing copies " + "(" + counter + " of " + workingCopies.size() + ")");
				for (IAttribute sourceAttribute : sourceAttributes) {
					IValueProcessor<Object> processor= (IValueProcessor<Object>)ValueProcessors.getProcessor(sourceAttribute);
					IAttribute targetAttribute= targetAttributes.findAttribute(sourceAttribute.getIdentifier(), singleMonitor);
					IWorkItem source= fContext.targetContext.getPair(target.getWorkItem());
					if (source.hasAttribute(sourceAttribute)) {
						processor.prepareTargetValue(target.getWorkItem(), targetAttribute, sourceAttribute, source.getValue(sourceAttribute), fContext, singleMonitor);
					}
				}
				counter++;
				singleMonitor.done();
			}
			fContext.sourceContext.itemResolver.execute(preparingMonitor);
			fContext.targetContext.itemResolver.execute(preparingMonitor);
			preparingMonitor.done();
		}
		return workingCopies;
	}

	private void copyTargets(List<WorkItemWorkingCopy> workingCopies, String message, SubMonitor monitor) throws TeamRepositoryException {
		BatchIterator<WorkItemWorkingCopy> iterator= new BatchIterator<WorkItemWorkingCopy>(workingCopies, 25);
		int counter= 0;
		while (iterator.hasNext()) {
			Collection<WorkItemWorkingCopy> batch= iterator.next();
			for (WorkItemWorkingCopy target : batch) {
				XMLString copiedFromCommentText= XMLString.createFromXMLText("Copied from " + createTextLink((IWorkItem)fContext.targetContext.getPair(target.getWorkItem())));
				IComment comment= target.getWorkItem().getComments().createComment(fContext.targetContext.auditableClient.getUser(), copiedFromCommentText);
				target.getWorkItem().getComments().append(comment);
			}
			SubMonitor saveMonitor= monitor.newChild(batch.size());
			saveMonitor.setTaskName(message + " (" + (counter + batch.size()) + " of " + workingCopies.size() + ")");
			fContext.targetContext.workingCopyManager.save(batch.toArray(new WorkItemWorkingCopy[batch.size()]), saveMonitor);
			saveMonitor.done();
			counter+= 25;
		}
	}

	private void copyLinks(List<WorkItemWorkingCopy> targets, SubMonitor linksMonitor) throws TeamRepositoryException {
		if (fContext.configuration.copyLinks || fContext.configuration.copyAttachments) {
			int counter= 1;
			for (WorkItemWorkingCopy target : targets) {
				SubMonitor singleMonitor= linksMonitor.newChild(1);
				String preparingMessage= "Copying links " + (fContext.configuration.copyAttachments ? "with attachments " : "");
				singleMonitor.setTaskName(preparingMessage + "(" + counter + " of " + targets.size() + ")");

				IWorkItemReferences sourceReferences= fContext.sourceContext.workingCopyManager.getWorkingCopy(fContext.targetContext.getPair(target.getWorkItem())).getReferences();
				IWorkItemReferences targetReferences= target.getReferences();
				for (IEndPointDescriptor endPoint : getEndPointsToCopy(sourceReferences)) {
					updateEndPoint(sourceReferences, targetReferences, endPoint, singleMonitor);
				}
				fContext.sourceContext.itemResolver.execute(singleMonitor);
				fContext.targetContext.itemResolver.execute(singleMonitor);

				if (!targetReferences.getChangedReferenceTypes().isEmpty()) {
					target.save(linksMonitor.newChild(1));
				}
				counter++;
			}
			linksMonitor.done();
		}
	}

	private WorkItemWorkingCopy newTarget(String type, IProgressMonitor monitor) throws TeamRepositoryException {
		IWorkItemType workitemType= fContext.targetContext.workItemClient.findWorkItemType(fContext.targetContext.projectArea, type, monitor);
		IWorkItemHandle workItemHandle= fContext.targetContext.workingCopyManager.connectNew(workitemType, monitor);
		return fContext.targetContext.workingCopyManager.getWorkingCopy(workItemHandle);
	}

	private List<IEndPointDescriptor> getEndPointsToCopy(IWorkItemReferences sourceReferences) {
		if (fContext.configuration.copyLinks) {
			List<IEndPointDescriptor> all= new ArrayList<IEndPointDescriptor>(sourceReferences.getTypes());
			if (!fContext.configuration.copyAttachments) {
				all.remove(WorkItemEndPoints.ATTACHMENT);
			}
			return all;
		}
		return Collections.singletonList(WorkItemEndPoints.ATTACHMENT);
	}

	private void updateEndPoint(IWorkItemReferences source, IWorkItemReferences target, IEndPointDescriptor endPoint, IProgressMonitor monitor) throws TeamRepositoryException {
		ILinkProcessor processor= LinkProcessors.getProcessor(endPoint);
		if (processor != null) {
			for (IReference reference : source.getReferences(endPoint)) {
				processor.prepareTargetLink(target, endPoint, reference, fContext, monitor);
			}
		}
	}

	private String createTextLink(IWorkItem workItem) {
		String linkText= WorkItemTextUtilities.getWorkItemText(workItem);
		String uri= Location.namedLocation(workItem, fContext.sourceContext.auditableClient.getPublicRepositoryURI()).toAbsoluteUri().toString();
		return String.format("<a href=\"%s\">%s</a>", uri, linkText); //$NON-NLS-1$
	}

	private static class BatchIterator<E> implements Iterator<Collection<E>> {

		private final List<E> fElements;
		private final int fBatchSize;

		public BatchIterator(Collection<E> elements, int batchSize) {
			fElements= new ArrayList<E>(elements);
			fBatchSize= batchSize;
		}

		@Override
		public boolean hasNext() {
			return !getNextChunk().isEmpty();
		}

		@Override
		public Collection<E> next() {
			Collection<E> nextChunk= getNextChunk();
			Collection<E> next= new ArrayList<E>(nextChunk);
			nextChunk.clear();
			return next;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

		private Collection<E> getNextChunk() {
			return fElements.subList(0, Math.min(fElements.size(), fBatchSize));
		}

	}
}
