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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import com.ibm.team.jface.util.UIUpdaterJob;
import com.ibm.team.process.common.IProjectAreaHandle;
import com.ibm.team.process.internal.rcp.ui.RepositoryLabelProvider;
import com.ibm.team.process.rcp.ui.teamnavigator.ConnectedProjectAreaRegistry;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.service.IPermissionService;
import com.ibm.team.workitem.rcp.ui.internal.ProjectAreaPicker;
import com.ibm.team.workitem.rcp.ui.internal.viewer.ItemComparer;

@SuppressWarnings({ "restriction", "unchecked", "rawtypes" })
public class ProjectAreaSelectionPage extends WizardPage {

	private final EvaluationContext fContext;
	private TableViewer fViewer;
	private ComboViewer fComboViewer;

	public ProjectAreaSelectionPage(EvaluationContext context) {
		super("Select Project Area", "Select Project Area", null);
		fContext= context;
	}

	public void createControl(Composite parent) {
		initializeDialogUnits(parent);

		Composite container= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		container.setLayout(layout);

		List<ITeamRepository> repos= getRepositories();
		if (repos.isEmpty()) {
			Label label= new Label(container, SWT.NONE);
			label.setText("No repositories available");
		}

		fComboViewer= createRepositoryPart(container, repos);

		Label label= new Label(container, SWT.NONE);
		label.setText("Project Areas");

		fViewer= new TableViewer(container, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		fViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		fViewer.setContentProvider(new SimpleContentProvider());
		fViewer.setSorter(new ViewerSorter());
		fViewer.setComparer(new ItemComparer());

		fComboViewer.setSelection(new StructuredSelection(repos.get(0)));

		setControl(container);
		Dialog.applyDialogFont(container);
	}

	private List<ITeamRepository> getRepositories() {
		return Arrays.asList(TeamPlatform.getTeamRepositoryService().getTeamRepositories());
	}

	private ComboViewer createRepositoryPart(Composite container, final List<ITeamRepository> repos) {
		Label repoLabel= new Label(container, SWT.NONE);
		repoLabel.setText("Repository:");

		Combo repoCombo= new Combo(container, SWT.SINGLE | SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		repoCombo.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		ComboViewer comboViewer= new ComboViewer(repoCombo);
		comboViewer.setContentProvider(new ArrayContentProvider());
		comboViewer.setLabelProvider(new RepositoryLabelProvider());
		comboViewer.setInput(repos);
		comboViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				withRepository((ITeamRepository)((IStructuredSelection)event.getSelection()).getFirstElement());
			}
		});
		return comboViewer;
	}

	private void withRepository(ITeamRepository repository) {
		fContext.message= null;
		fContext.targetContext= new RepositoryContext(true, repository);
		setPageComplete(false);
		fViewer.setInput("");
		setErrorMessage(null);

		new UIUpdaterJob("") {
			@Override
			public IStatus runInBackground(IProgressMonitor monitor) {
				try {
					// Provide a way to override the constraint that user should
					// be a member of JAZZ_ADMINS
					boolean allowNonAdminUsers = Boolean.getBoolean("allowNonAdminUsers");
					fContext.targetContext.isAdmin = allowNonAdminUsers || fContext.targetContext.teamRepository
							.externalUserRegistryManager().isMember(
									fContext.targetContext.teamRepository.getUserId(), IPermissionService.JAZZ_ADMINS,
									new SubProgressMonitor(monitor, 200));
				} catch (TeamRepositoryException e) {
					fContext.message= e.getMessage();
				}
				return Status.OK_STATUS;
			}

			@Override
			public IStatus runInUI(IProgressMonitor monitor) {
				computeProjectAreas();
				return Status.OK_STATUS;
			}
		}.schedule();
	}

	private void computeProjectAreas() {
		if (fContext.message != null) {
			setErrorMessage(fContext.message);
			return;
		}

		if (fContext.targetContext == null) {
			setErrorMessage("Not connected to any project areas");
			return;
		}

		// commented this because was getting this error even though was admin
		// if (!fContext.targetContext.isAdmin) {
		if (fContext.targetContext.isAdmin) {
			setErrorMessage("This operation requires Admin role in the selected repository");
			return;
		}

		List<IProjectAreaHandle> connectedProjectAreas= getProjectAreasToSelect();
		if (connectedProjectAreas.isEmpty()) {
			setErrorMessage("Not connected to any project areas");
			return;
		}

		fViewer.setLabelProvider(new ProjectAreaPicker.ProjectAreaLabelProvider(connectedProjectAreas));
		fViewer.setInput(connectedProjectAreas);
		fViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				getContainer().showPage(getNextPage());
			}
		});
		fViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection selection= (IStructuredSelection)event.getSelection();
				fContext.targetContext.projectArea= (IProjectAreaHandle)selection.getFirstElement();
				setPageComplete(true);
			}
		});

		fViewer.setSelection(new StructuredSelection(fViewer.getElementAt(0)));
	}

	@Override
	public boolean canFlipToNextPage() {
		return fContext.targetContext != null && fContext.targetContext.projectArea != null;
	}

	private static class SimpleContentProvider implements IStructuredContentProvider {
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof Collection)
				return ((Collection)inputElement).toArray();
			return new Object[] { inputElement };
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}

		public void dispose() {}
	}

	private List<IProjectAreaHandle> getProjectAreasToSelect() {
		List<IProjectAreaHandle> result= new ArrayList<IProjectAreaHandle>();
		for (IProjectAreaHandle projectArea : (List<IProjectAreaHandle>)ConnectedProjectAreaRegistry.getDefault().getConnectedProjectAreas(fContext.targetContext.teamRepository)) {
			if (!fContext.queryDescriptor.getProjectArea().sameItemId(projectArea)) {
				result.add(projectArea);
			}
		}
		return result;
	}
}
