package org.syncany.gui.history;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.ocpsoft.prettytime.PrettyTime;
import org.syncany.config.GuiEventBus;
import org.syncany.database.DatabaseVersionHeader;
import org.syncany.database.FileVersion;
import org.syncany.gui.Panel;
import org.syncany.gui.util.I18n;
import org.syncany.gui.util.SWTResourceManager;
import org.syncany.operations.daemon.Watch;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderRequest;
import org.syncany.operations.daemon.messages.GetDatabaseVersionHeadersFolderResponse;
import org.syncany.operations.daemon.messages.ListWatchesManagementRequest;
import org.syncany.operations.daemon.messages.ListWatchesManagementResponse;

import com.google.common.eventbus.Subscribe;

/**
 * @author Philipp C. Heckel <philipp.heckel@gmail.com>
 */
public class MainPanel extends Panel {
	private static final String IMAGE_RESOURCE_FORMAT = "/" + MainPanel.class.getPackage().getName().replace('.', '/') + "/%s.png";
	
	private MainPanelState state;
	
	private Combo rootSelectCombo;
	private Label dateLabel;
	private Scale dateSlider;
	private StackLayout stackLayout;
	private Composite stackComposite;
	private FileTreeComposite fileTreeComposite;
	private LogComposite logComposite;
	
	private boolean dateLabelPrettyTime;
	private AtomicInteger dateSliderValue;
	private Timer dateSliderChangeTimer;
	
	private Button toggleTreeButton;
	private Button toggleLogButton;
	
	private ListWatchesManagementRequest pendingListWatchesRequest;

	private GuiEventBus eventBus;

	public MainPanel(HistoryDialog parentDialog, Composite composite, int style) {
		super(parentDialog, composite, style);

		this.setBackgroundImage(null);
		this.setBackgroundMode(SWT.INHERIT_DEFAULT);
		
		this.state = new MainPanelState();
		
		this.dateLabelPrettyTime = true;
		this.dateSliderValue = new AtomicInteger(-1);
		this.dateSliderChangeTimer = null;
		
		this.pendingListWatchesRequest = null;
		
		this.eventBus = GuiEventBus.getInstance();
		this.eventBus.register(this);	
		
		this.createContents();
	}
	
	public HistoryDialog getParentDialog() {
		return (HistoryDialog) parentDialog;
	}

	private void createContents() {
		refreshRoots();
		
		createMainComposite();
		createToggleButtons();
		createRootSelectionCombo();
		createDateSlider();
		createStackComposite();
		createFileTreeComposite();
		createLogComposite();
		
		setCurrentControl(fileTreeComposite);
	}	

	private void createMainComposite() {
		GridLayout mainCompositeGridLayout = new GridLayout(5, false);
		mainCompositeGridLayout.marginTop = 0;
		mainCompositeGridLayout.marginLeft = 0;
		mainCompositeGridLayout.marginRight = 0;

		setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1));
		setLayout(mainCompositeGridLayout);
	}
	
	private void createToggleButtons() {
		toggleTreeButton = new Button(this, SWT.TOGGLE);
		toggleTreeButton.setSelection(true);
		toggleTreeButton.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "tree")));
		
		toggleLogButton = new Button(this, SWT.TOGGLE);
		toggleLogButton.setSelection(false);
		toggleLogButton.setImage(SWTResourceManager.getImage(String.format(IMAGE_RESOURCE_FORMAT, "log")));
		
		toggleTreeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showTree();				
			}
		});		
		
		toggleLogButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				showLog();				
			}
		});
	}
	
	public void showLog() {
		setCurrentControl(logComposite);

		toggleTreeButton.setSelection(false);
		toggleLogButton.setSelection(true);
	}

	public void showTree() {
		setCurrentControl(fileTreeComposite);
		
		toggleTreeButton.setSelection(true);
		toggleLogButton.setSelection(false);
	}

	private void createRootSelectionCombo() {
		rootSelectCombo = new Combo(this, SWT.DROP_DOWN | SWT.BORDER | SWT.READ_ONLY);
		
		rootSelectCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		rootSelectCombo.setText(I18n.getText("org.syncany.gui.history.HistoryDialog.retrievingList"));
		rootSelectCombo.setEnabled(false);
		
		rootSelectCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ListWatchesManagementResponse listWatchesResponse = (ListWatchesManagementResponse) rootSelectCombo.getData();				
				
				if (listWatchesResponse != null) {
					List<Watch> watches = listWatchesResponse.getWatches();
					int selectionIndex = rootSelectCombo.getSelectionIndex();

					if (selectionIndex >= 0 && selectionIndex < watches.size()) {
						state.setSelectedRoot(watches.get(selectionIndex).getFolder().getAbsolutePath());
						state.setSelectedDate(null);
						state.setSelectedFileVersion(null);
						state.getExpandedFilePaths().clear();
						
						refreshDateSlider();
						
						fileTreeComposite.resetAndRefresh();
						logComposite.resetAndRefresh();
					}
				}
			}
		});
	}
	
	private void createDateSlider() {
		// Label
		GridData dateLabelGridData = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		dateLabelGridData.minimumWidth = 150;
		
		dateLabel = new Label(this, SWT.CENTER);
		dateLabel.setLayoutData(dateLabelGridData);
		
		dateLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent e) {
				dateLabelPrettyTime = !dateLabelPrettyTime;
				
				if (dateLabel.getData() != null) {
					setDateLabel((Date) dateLabel.getData());
				}
			}
		});
		
		// Slider
		dateSlider = new Scale(this, SWT.HORIZONTAL | SWT.BORDER);
		
		dateSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		dateSlider.setEnabled(false);
		
		dateSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {				
				synchronized (dateSlider) {	
					int newDateSliderValue = dateSlider.getSelection();
					Date newSliderDate = getDateSliderDate();

					boolean dateSliderValueChanged = dateSliderValue.get() != newDateSliderValue;
					
					if (dateSliderValueChanged) {
						// Update cached value
						dateSliderValue.set(newDateSliderValue);
						
						// Update label right away
						setDateLabel(newSliderDate);
						logComposite.highlightByDate(newSliderDate);

						// Update file tree after a while  
						if (dateSliderChangeTimer != null) {
							dateSliderChangeTimer.cancel();
						}
						
						dateSliderChangeTimer = new Timer();
						dateSliderChangeTimer.schedule(createDateSliderTimerTask(), 800);
					}
				}
			}
		});		
	}
	
	private void createStackComposite() {
		stackLayout = new StackLayout();
		stackLayout.marginHeight = 0;
		stackLayout.marginWidth = 0;
		
		GridData stackCompositeGridData = new GridData(SWT.FILL, SWT.FILL, true, true, 5, 1);
		stackCompositeGridData.minimumWidth = 500;

		stackComposite = new Composite(this, SWT.DOUBLE_BUFFERED);
		stackComposite.setLayout(stackLayout);
		stackComposite.setLayoutData(stackCompositeGridData);
	}
	
	private void createFileTreeComposite() {
		fileTreeComposite = new FileTreeComposite(this, state, stackComposite, SWT.NONE);
	}

	private void createLogComposite() {
		logComposite = new LogComposite(this, state, stackComposite, SWT.NONE);
	}

	private void setCurrentControl(Control control) {
		stackLayout.topControl = control;
		stackComposite.layout();	
	}
	
	private void setDateLabel(final Date dateSliderDate) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {				
				String dateStrPretty = new PrettyTime().format(dateSliderDate);
				String dateStrExact = dateSliderDate.toString();
				
				dateLabel.setData(dateSliderDate);
				
				if (dateLabelPrettyTime) {
					dateLabel.setText(dateStrPretty);
					dateLabel.setToolTipText(dateStrExact);
				}
				else {
					dateLabel.setText(dateStrExact);
					dateLabel.setToolTipText(dateStrPretty);
				}
			}
		});
	}

	private TimerTask createDateSliderTimerTask() {
		return new TimerTask() {			
			@Override
			public void run() {		
				Display.getDefault().syncExec(new Runnable() {
					@Override
					public void run() {	
						Date newDate = getDateSliderDate();						
						boolean listUpdateRequired = !newDate.equals(state.getSelectedDate());
						
						if (listUpdateRequired) {
							updateDate(newDate);							
						}
					}					
				});
			}
		};
	}
	
	public void updateDate(Date newDate) {
		state.setSelectedDate(newDate);
		
		setDateSlider(newDate);
		setDateLabel(newDate);
		
		fileTreeComposite.resetAndRefresh();
		logComposite.highlightBySelectedDate();
	}
	
	@SuppressWarnings("unchecked")
	private Date getDateSliderDate() {
		List<DatabaseVersionHeader> headers = (List<DatabaseVersionHeader>) dateSlider.getData();
		
		int dateSelectionIndex = dateSlider.getSelection();
		
		if (dateSelectionIndex >= 0 && dateSelectionIndex < headers.size()) {
			return headers.get(dateSelectionIndex).getDate();
		}
		else {
			return null;
		}
	}
	
	@SuppressWarnings("unchecked")
	private void setDateSlider(Date newDate) {
		List<DatabaseVersionHeader> headers = (List<DatabaseVersionHeader>) dateSlider.getData();
		
		for (int i = 0; i < headers.size(); i++) {
			DatabaseVersionHeader header = headers.get(i);
			
			if (header.getDate().equals(newDate)) {
				dateSlider.setSelection(i);
			}
		}
	}
	
	public void showDetails(FileVersion fileVersion) {
		getParentDialog().showDetails(state.getSelectedRoot(), fileVersion.getFileHistoryId());
	}

	public void safeDispose() {
		fileTreeComposite.safeDispose();
		logComposite.safeDispose();
		
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {	
				eventBus.unregister(MainPanel.this);
			}
		});
	}	
	
	private void refreshRoots() {
		pendingListWatchesRequest = new ListWatchesManagementRequest();
		eventBus.post(pendingListWatchesRequest);		
	}

	@Subscribe
	public void onListWatchesManagementResponse(final ListWatchesManagementResponse listWatchesResponse) {
		if (pendingListWatchesRequest != null && pendingListWatchesRequest.getId() == listWatchesResponse.getRequestId()) {
			// Nullify pending request
			pendingListWatchesRequest = null;

			// Update combo box
			Display.getDefault().syncExec(new Runnable() {
				@Override
				public void run() {
					ArrayList<Watch> watches = listWatchesResponse.getWatches();
					
					rootSelectCombo.removeAll();
					
					for (Watch watch : watches) {
						rootSelectCombo.add(watch.getFolder().getName());
					}
					
					rootSelectCombo.setData(listWatchesResponse);
					rootSelectCombo.setEnabled(true);
					
					if (rootSelectCombo.getItemCount() > 0) {
						state.setSelectedRoot(watches.get(0).getFolder().getAbsolutePath());
						rootSelectCombo.select(0);
						
						refreshDateSlider();
						fileTreeComposite.resetAndRefresh();
						logComposite.resetAndRefresh();
					}
				}
			});
		}
	}
	
	private void refreshDateSlider() {
		GetDatabaseVersionHeadersFolderRequest getHeadersRequest = new GetDatabaseVersionHeadersFolderRequest();
		getHeadersRequest.setRoot(state.getSelectedRoot());
		
		eventBus.post(getHeadersRequest);
	}
	
	@Subscribe
	public void onGetDatabaseVersionHeadersFolderResponse(final GetDatabaseVersionHeadersFolderResponse getHeadersResponse) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				List<DatabaseVersionHeader> headers = getHeadersResponse.getDatabaseVersionHeaders();
				
				if (headers.size() > 0) {
					int maxValue = headers.size() - 1;
					Date newSelectedDate = headers.get(headers.size()-1).getDate();
					
					dateSlider.setData(headers);
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(maxValue);
					dateSlider.setSelection(maxValue);
					dateSlider.setEnabled(true);
					
					state.setSelectedDate(newSelectedDate);	
					setDateLabel(newSelectedDate);
				}
				else {
					dateSlider.setMinimum(0);
					dateSlider.setMaximum(0);
					dateSlider.setEnabled(false);	
					
					state.setSelectedDate(null);
				}				
			}
		});
	}

	@Override
	public boolean validatePanel() {
		return true;
	}		
}
