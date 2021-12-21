package jadx.gui.ui.dialog;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumn;

import hu.akarnokd.rxjava2.swing.SwingSchedulers;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import jadx.gui.treemodel.JNode;
import jadx.gui.treemodel.JResSearchNode;
import jadx.gui.ui.MainWindow;
import jadx.gui.ui.panel.ProgressPanel;
import jadx.gui.utils.NLS;
import jadx.gui.utils.TextStandardActions;
import jadx.gui.utils.layout.WrapLayout;
import jadx.gui.utils.search.SearchSettings;
import jadx.gui.utils.search.TextSearchIndex;

import jadx.gui.ui.dialog.SearchDialog.SearchOptions;
import jadx.gui.ui.dialog.SearchDialog.SearchPreset;

public class AdvSearchDialog extends CommonSearchDialog {
	private static final long serialVersionUID = -5105405456969134105L;
	private static final Color SEARCHFIELD_ERROR_COLOR = new Color(255, 150, 150);

	private static final Logger LOG = LoggerFactory.getLogger(AdvSearchDialog.class);

	public static void search(MainWindow window, SearchPreset preset) {
		AdvSearchDialog searchDialog = new AdvSearchDialog(window, preset, Collections.emptySet());
		searchDialog.setVisible(true);
	}

	public static void searchInActiveTab(MainWindow window, SearchPreset preset) {
		AdvSearchDialog searchDialog = new AdvSearchDialog(window, preset, EnumSet.of(SearchOptions.ACTIVE_TAB));
		searchDialog.setVisible(true);
	}

	public static void searchText(MainWindow window, String text) {
		AdvSearchDialog searchDialog = new AdvSearchDialog(window, SearchPreset.TEXT, Collections.emptySet());
		searchDialog.initSearchText = text;
		searchDialog.setVisible(true);
	}

//	public enum SearchPreset {
//		TEXT, CLASS, COMMENT
//	}
//
//	public enum SearchOptions {
//		CLASS,
//		METHOD,
//		FIELD,
//		CODE,
//		RESOURCE,
//		COMMENT,
//
//		IGNORE_CASE,
//		USE_REGEX,
//		ACTIVE_TAB
//	}

	private final transient SearchPreset searchPreset;
	private final transient Set<SearchOptions> options;

	private Color searchFieldDefaultBgColor;

	private transient JTextField searchField;
	private transient ResultsText searchResultArea;

	private transient Disposable searchDisposable;
	private transient SearchEventEmitter searchEmitter;
	private transient ChangeListener activeTabListener;

	private transient String initSearchText = null;

	private AdvSearchDialog(MainWindow mainWindow, SearchPreset preset, Set<SearchOptions> additionalOptions) {
		super(mainWindow, NLS.str("adv.menu.text_search"));
		this.searchPreset = preset;
		this.options = buildOptions(preset);
		this.options.addAll(additionalOptions);

		loadWindowPos();
		initUI();
		searchFieldSubscribe();
		registerInitOnOpen();
		registerActiveTabListener();
	}

	@Override
	public void dispose() {
		if (searchDisposable != null && !searchDisposable.isDisposed()) {
			searchDisposable.dispose();
		}
		removeActiveTabListener();
		super.dispose();
	}

	private Set<SearchOptions> buildOptions(SearchPreset preset) {
		Set<SearchOptions> searchOptions = cache.getLastSearchOptions().get(preset);
		if (searchOptions == null) {
			searchOptions = new HashSet<>();
		}
		switch (preset) {
			case TEXT:
				if (searchOptions.isEmpty()) {
					searchOptions.add(SearchOptions.CODE);
					searchOptions.add(SearchOptions.IGNORE_CASE);
					searchOptions.add(SearchOptions.RESOURCE);
					searchOptions.add(SearchOptions.FIELD);
				}
				break;

			case CLASS:
				searchOptions.add(SearchOptions.CLASS);
				break;

			case COMMENT:
				searchOptions.add(SearchOptions.COMMENT);
				searchOptions.remove(SearchOptions.ACTIVE_TAB);
				break;
		}
		return searchOptions;
	}

	@Override
	protected void openInit() {
		String searchText = initSearchText != null ? initSearchText : cache.getLastSearch();
		if (searchText != null) {
			searchField.setText(searchText);
			searchField.selectAll();
		}
		searchField.requestFocus();

		if (searchField.getText().isEmpty()) {
			checkIndex();
		}
		searchEmitter.emitSearch();
	}

	private TextSearchIndex checkIndex() {
		if (!cache.getIndexService().isComplete()) {
			if (isFullIndexNeeded()) {
				prepare();
			}
		}
		return cache.getTextIndex();
	}

	private boolean isFullIndexNeeded() {
		for (SearchOptions option : options) {
			switch (option) {
				case CLASS:
				case METHOD:
				case FIELD:
					// TODO: split indexes so full decompilation not needed for these
					return true;

				case CODE:
					return true;

				case RESOURCE:
				case COMMENT:
					// full index not needed
					break;
			}
		}
		return false;
	}

	private void initUI() {
		searchField = new JTextField();
		searchFieldDefaultBgColor = searchField.getBackground();
		searchField.setAlignmentX(LEFT_ALIGNMENT);
		TextStandardActions.attach(searchField);

		JLabel findLabel = new JLabel(NLS.str("adv.search_dialog.open_by_name"));
		findLabel.setAlignmentX(LEFT_ALIGNMENT);

		JPanel searchFieldPanel = new JPanel();
		searchFieldPanel.setLayout(new BoxLayout(searchFieldPanel, BoxLayout.PAGE_AXIS));
		searchFieldPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
		searchFieldPanel.setAlignmentX(LEFT_ALIGNMENT);
		searchFieldPanel.add(findLabel);
		searchFieldPanel.add(Box.createRigidArea(new Dimension(0, 5)));
		searchFieldPanel.add(searchField);

		JPanel searchInPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		searchInPanel.setBorder(BorderFactory.createTitledBorder(NLS.str("search_dialog.search_in")));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.class"), SearchOptions.CLASS));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.method"), SearchOptions.METHOD));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.field"), SearchOptions.FIELD));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.code"), SearchOptions.CODE));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.resource"), SearchOptions.RESOURCE));
		searchInPanel.add(makeOptionsCheckBox(NLS.str("search_dialog.comments"), SearchOptions.COMMENT));

		JPanel searchOptions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		searchOptions.setBorder(BorderFactory.createTitledBorder(NLS.str("search_dialog.options")));
		searchOptions.add(makeOptionsCheckBox(NLS.str("search_dialog.ignorecase"), SearchOptions.IGNORE_CASE));
		searchOptions.add(makeOptionsCheckBox(NLS.str("search_dialog.regex"), SearchOptions.USE_REGEX));
//		searchOptions.add(makeOptionsCheckBox(NLS.str("search_dialog.active_tab"), SearchOptions.ACTIVE_TAB));

		JPanel optionsPanel = new JPanel(new WrapLayout(WrapLayout.LEFT));
		optionsPanel.setAlignmentX(LEFT_ALIGNMENT);
		optionsPanel.add(searchInPanel);
		optionsPanel.add(searchOptions);

		JPanel searchPane = new JPanel();
		searchPane.setLayout(new BoxLayout(searchPane, BoxLayout.PAGE_AXIS));
		searchPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		searchPane.add(searchFieldPanel);
		searchPane.add(Box.createRigidArea(new Dimension(0, 5)));
		searchPane.add(optionsPanel);

		initCommon();
		JPanel resultsPanel = initResultsTable();
		JPanel buttonPane = initButtonsPanel();

		Container contentPane = getContentPane();
		contentPane.add(searchPane, BorderLayout.PAGE_START);
		contentPane.add(resultsPanel, BorderLayout.CENTER);
		contentPane.add(buttonPane, BorderLayout.PAGE_END);

		searchField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					if (resultsModel.getRowCount() != 0) {
						resultsTable.setRowSelectionInterval(0, 0);
					}
					resultsTable.requestFocus();
				}
			}
		});
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	}

	@Override
	@NotNull
	protected JPanel initButtonsPanel() {
		progressPane = new ProgressPanel(mainWindow, false);

		JButton cancelButton = new JButton(NLS.str("search_dialog.cancel"));
		cancelButton.addActionListener(event -> dispose());
		JButton openBtn = new JButton(NLS.str("adv.search_dialog.copy"));
		openBtn.addActionListener(event -> copyResult());
		getRootPane().setDefaultButton(openBtn);

		JPanel buttonPane = new JPanel();
		buttonPane.setLayout(new BoxLayout(buttonPane, BoxLayout.LINE_AXIS));
		buttonPane.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

		JCheckBox cbKeepOpen = new JCheckBox(NLS.str("search_dialog.keep_open"));
		cbKeepOpen.setSelected(mainWindow.getSettings().getKeepCommonDialogOpen());
		cbKeepOpen.addActionListener(e -> {
			mainWindow.getSettings().setKeepCommonDialogOpen(cbKeepOpen.isSelected());
			mainWindow.getSettings().sync();
		});
		buttonPane.add(cbKeepOpen);
		buttonPane.add(Box.createRigidArea(new Dimension(15, 0)));
		buttonPane.add(progressPane);
		buttonPane.add(Box.createRigidArea(new Dimension(5, 0)));
		buttonPane.add(Box.createHorizontalGlue());
		buttonPane.add(openBtn);
		buttonPane.add(Box.createRigidArea(new Dimension(10, 0)));
		buttonPane.add(cancelButton);
		return buttonPane;
	}

	private void copyResult() {
		searchResultArea.getText();
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(searchResultArea.getText()), null);
	}

	protected synchronized void performSearch(List<JNode> results) {
		searchResultArea.updateData(results);
	}

	@Override
	protected JPanel initResultsTable() {
		ResultsTableCellRenderer renderer = new ResultsTableCellRenderer();
		resultsModel = new ResultsModel(renderer);
		resultsModel.addTableModelListener(e -> updateProgressLabel());

		resultsTable = new ResultsTable(resultsModel, renderer);
		resultsTable.setShowHorizontalLines(false);
		resultsTable.setDragEnabled(false);
		resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		// resultsTable.setBackground(CodeArea.CODE_BACKGROUND);
		resultsTable.setColumnSelectionAllowed(false);
		resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		resultsTable.setAutoscrolls(false);

		resultsTable.setDefaultRenderer(Object.class, renderer);
		Enumeration<TableColumn> columns = resultsTable.getColumnModel().getColumns();
		while (columns.hasMoreElements()) {
			TableColumn column = columns.nextElement();
			column.setCellRenderer(renderer);
		}

		resultsTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent evt) {
				if (evt.getClickCount() == 2) {
					openSelectedItem();
				}
			}
		});
		resultsTable.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					openSelectedItem();
				}
			}
		});

		warnLabel = new JLabel();
		warnLabel.setForeground(Color.RED);
		warnLabel.setVisible(false);

		searchResultArea = new ResultsText();

		JPanel resultsPanel = new JPanel();
		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.PAGE_AXIS));
		resultsPanel.add(warnLabel);
		resultsPanel.add(new JScrollPane(searchResultArea,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED));



		resultsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
		return resultsPanel;
	}

	protected static class ResultsText extends JTextArea {

		public void updateData(List<JNode> results) {
			StringBuilder sb = new StringBuilder();
			for (JNode jNode: results) {
				if(!jNode.hasDescString()) {
					sb.append(jNode.makeString()).append("\n");
				} else {
//					if(jNode instanceof JResSearchNode) {
//						sb.append(jNode.makeString()).append(" : ");
//					}
					sb.append(jNode.makeDescString()).append("\n");
				}
			}
			this.setText(sb.toString());
			this.setCaretPosition(0);
		}
	}

	private class SearchEventEmitter {
		private final Flowable<String> flowable;
		private Emitter<String> emitter;

		public SearchEventEmitter() {
			flowable = Flowable.create(this::saveEmitter, BackpressureStrategy.LATEST);
		}

		public Flowable<String> getFlowable() {
			return flowable;
		}

		private void saveEmitter(Emitter<String> emitter) {
			this.emitter = emitter;
		}

		public synchronized void emitSearch() {
			this.emitter.onNext(searchField.getText());
		}
	}

	private void searchFieldSubscribe() {
		searchEmitter = new SearchEventEmitter();

		Flowable<String> textChanges = onTextFieldChanges(searchField);
		Flowable<String> searchEvents = Flowable.merge(textChanges, searchEmitter.getFlowable());
		searchDisposable = searchEvents
				.subscribeOn(Schedulers.single())
				.switchMap(text -> prepareSearch(text)
						.doOnError(e -> LOG.error("Error prepare search: {}", e.getMessage(), e))
						.subscribeOn(Schedulers.single())
						.toList()
						.toFlowable(), 1)
				.observeOn(SwingSchedulers.edt())
				.doOnError(e -> LOG.error("Error while searching: {}", e.getMessage(), e))
				.subscribe(this::processSearchResults);
	}

	private Flowable<JNode> prepareSearch(String text) {
		if (text == null || options.isEmpty()) {
			return Flowable.empty();
		}
		// allow empty text for comments search
		if (text.isEmpty() && !options.contains(SearchOptions.COMMENT)) {
			return Flowable.empty();
		}

		TextSearchIndex index = checkIndex();
		if (index == null) {
			return Flowable.empty();
		}
		LOG.debug("search event: {}", text);
//		showSearchState();
		try {
			Flowable<JNode> result = index.buildSearch(text, options);
			if (searchField.getBackground() == SEARCHFIELD_ERROR_COLOR) {
				searchField.setBackground(searchFieldDefaultBgColor);
			}
			return result;
		} catch (SearchSettings.InvalidSearchTermException e) {
			searchField.setBackground(SEARCHFIELD_ERROR_COLOR);
			return Flowable.empty();
		}
	}

	private void processSearchResults(java.util.List<JNode> results) {
		LOG.debug("search result size: {}", results.size());
		String text = searchField.getText();
		setHighlightText(text);
		highlightTextCaseInsensitive = options.contains(SearchOptions.IGNORE_CASE);
		highlightTextUseRegex = options.contains(SearchOptions.USE_REGEX);

		cache.setLastSearch(text);
		cache.getLastSearchOptions().put(searchPreset, options);

		resultsModel.clear();
		resultsModel.addAll(results);
		performSearch(results);
	}

	private static Flowable<String> onTextFieldChanges(final JTextField textField) {
		return Flowable.<String>create(emitter -> {
			DocumentListener listener = new DocumentListener() {
				@Override
				public void insertUpdate(DocumentEvent e) {
					change();
				}

				@Override
				public void removeUpdate(DocumentEvent e) {
					change();
				}

				@Override
				public void changedUpdate(DocumentEvent e) {
					change();
				}

				public void change() {
					emitter.onNext(textField.getText());
				}
			};

			textField.getDocument().addDocumentListener(listener);
			emitter.setDisposable(new Disposable() {
				private boolean disposed = false;

				@Override
				public void dispose() {
					textField.getDocument().removeDocumentListener(listener);
					disposed = true;
				}

				@Override
				public boolean isDisposed() {
					return disposed;
				}
			});
		}, BackpressureStrategy.LATEST)
				.debounce(300, TimeUnit.MILLISECONDS)
				.distinctUntilChanged();
	}

	private JCheckBox makeOptionsCheckBox(String name, final SearchOptions opt) {
		final JCheckBox chBox = new JCheckBox(name);
		chBox.setAlignmentX(LEFT_ALIGNMENT);
		chBox.setSelected(options.contains(opt));
		chBox.addItemListener(e -> {
			if (chBox.isSelected()) {
				options.add(opt);
			} else {
				options.remove(opt);
			}
			searchEmitter.emitSearch();
		});
		return chBox;
	}

	@Override
	protected void loadFinished() {
		resultsTable.setEnabled(true);
		searchField.setEnabled(true);
		searchEmitter.emitSearch();
	}

	@Override
	protected void loadStart() {
		resultsTable.setEnabled(false);
		searchField.setEnabled(false);
	}

	private void registerActiveTabListener() {
		removeActiveTabListener();
		activeTabListener = e -> {
			if (options.contains(SearchOptions.ACTIVE_TAB)) {
				LOG.debug("active tab change event received");
				searchEmitter.emitSearch();
			}
		};
		mainWindow.getTabbedPane().addChangeListener(activeTabListener);
	}

	private void removeActiveTabListener() {
		if (activeTabListener != null) {
			mainWindow.getTabbedPane().removeChangeListener(activeTabListener);
			activeTabListener = null;
		}
	}
}
