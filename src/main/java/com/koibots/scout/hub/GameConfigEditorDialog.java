package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.koibots.scout.hub.GameConfig.Field;
import com.koibots.scout.hub.GameConfig.Section;

public class GameConfigEditorDialog
    extends JDialog
{
    private static final long serialVersionUID = -716394164351569605L;

    private GameConfigTreeModel model;

    public GameConfigEditorDialog(Frame owner,
                                  GameConfig config,
                                  GameConfigChangeListener listener)
    {
        super(owner, "Game Config Editor", true);

        model = new GameConfigTreeModel(config);
        if(null != listener) {
            model.addChangeListener(listener);
        }

        JTree tree = new JTree(model);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new GameConfigTransferHandler());

        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean sel,
                    boolean expanded, boolean leaf, int row, boolean hasFocus) {

                super.getTreeCellRendererComponent(
                        tree, value, sel, expanded, leaf, row, hasFocus);

                DefaultMutableTreeNode node =
                        (DefaultMutableTreeNode) value;

                Object obj = node.getUserObject();
                if (obj instanceof Section) {
                    setText(((Section) obj).getName());
                } else if (obj instanceof Field) {
                    setText(((Field) obj).getTitle());
                }

                return this;
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Double-click -> edit the selected tree node
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    editSelectedNode(tree);
                }
            }
        });


        add(new JScrollPane(tree), BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        add(close, BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        setSize(400, 500);
        setLocationRelativeTo(owner);
    }

    public void addGameConfigChangeListener(GameConfigChangeListener listener) {
        model.addChangeListener(listener);
    }

    public interface GameConfigChangeListener {

        /**
         * Called before the move happens.
         * Throw an exception to veto the change.
         */
        void beforeMove(MoveEvent event) throws Exception;

        /** Called after the move has been applied */
        void afterMove(MoveEvent event);
    }

    public class MoveEvent {
        public final Object moved;
        public final Object oldParent;
        public final Object newParent;
        public final int oldIndex;
        public final int newIndex;

        public MoveEvent(Object moved, Object oldParent, Object newParent,
                         int oldIndex, int newIndex) {
            this.moved = moved;
            this.oldParent = oldParent;
            this.newParent = newParent;
            this.oldIndex = oldIndex;
            this.newIndex = newIndex;
        }
    }

    public class GameConfigTreeModel extends DefaultTreeModel {

        private static final long serialVersionUID = 7909699472552723977L;
        private final List<GameConfigChangeListener> listeners = new ArrayList<>();

        public GameConfigTreeModel(GameConfig config) {
            super(buildRoot(config));
        }

        public void addChangeListener(GameConfigChangeListener l) {
            listeners.add(l);
        }

        private static DefaultMutableTreeNode buildRoot(GameConfig config) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");
            for (Section p : config.getSections()) {
                DefaultMutableTreeNode phaseNode = new DefaultMutableTreeNode(p);
                for (Field f : p.getFields()) {
                    phaseNode.add(new DefaultMutableTreeNode(f));
                }
                root.add(phaseNode);
            }
            return root;
        }

        public void moveNode(
                DefaultMutableTreeNode node,
                DefaultMutableTreeNode newParent,
                int index) throws Exception {

System.out.println("moveNode(" + node + ", " + newParent + ", " + index + ")");
            DefaultMutableTreeNode oldParent =
                    (DefaultMutableTreeNode) node.getParent();

            int oldIndex = oldParent.getIndex(node);

            Object moved = node.getUserObject();
            Object oldParentObj = oldParent.getUserObject();
            Object newParentObj = newParent.getUserObject();

            if(Objects.equals(oldParentObj, newParentObj)
                    && oldIndex == index) {
                System.out.println("Detected no-op move for item " + moved);
                return;
            }

            MoveEvent evt = new MoveEvent(
                    moved, oldParentObj, newParentObj, oldIndex, index
            );

            // veto point
            for (GameConfigChangeListener l : listeners) {
                l.beforeMove(evt);
            }

            // apply to tree
            System.out.println("Inserting node " + node + " into " + newParent + " at index " + index);
            removeNodeFromParent(node);

            if(Objects.equals(oldParent, newParent) && oldIndex < index) {
                index -= 1;
            }

            insertNodeInto(node, newParent, index);

            for (GameConfigChangeListener l : listeners) {
                l.afterMove(evt);
            }
        }
    }

    public class GameConfigTransferHandler extends TransferHandler {

        private static final long serialVersionUID = -3254733554183635522L;
        private final DataFlavor nodeFlavor;
        private DefaultMutableTreeNode draggedNode;

        public GameConfigTransferHandler() {
            try {
                nodeFlavor = new DataFlavor(
                        DataFlavor.javaJVMLocalObjectMimeType +
                                ";class=javax.swing.tree.DefaultMutableTreeNode");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            JTree tree = (JTree) c;
            draggedNode =
                    (DefaultMutableTreeNode) tree.getSelectionPath()
                            .getLastPathComponent();

            return new Transferable() {
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[]{nodeFlavor};
                }

                public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return flavor.equals(nodeFlavor);
                }

                public Object getTransferData(DataFlavor flavor) {
                    return draggedNode;
                }
            };
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;

            JTree.DropLocation dl =
                    (JTree.DropLocation) support.getDropLocation();

            // Target is the thing the dragged item is being dropped ON
            DefaultMutableTreeNode target =
                    (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

            Object dragged = draggedNode.getUserObject();
            Object targetObj = target.getUserObject();

            if (dragged instanceof Field) {
                // We can only drag-and-drop Fields onto sections
                return targetObj instanceof Section;
            } else if(dragged instanceof Section) {
                // We can only drop Sections onto the root
                return null == target.getParent();
            }

            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            try {
                JTree tree = (JTree) support.getComponent();
                GameConfigTreeModel model =
                        (GameConfigTreeModel) tree.getModel();

                JTree.DropLocation dl =
                        (JTree.DropLocation) support.getDropLocation();

                // Target is the thing the dragged item is being dropped ON
                DefaultMutableTreeNode target =
                        (DefaultMutableTreeNode) dl.getPath().getLastPathComponent();

                DefaultMutableTreeNode newParent;
                if(draggedNode.getUserObject() instanceof Section) {
                    newParent = (DefaultMutableTreeNode)model.getRoot();
                } else {
                    newParent = target;
                }

                int index = dl.getChildIndex();
                if (index == -1) {
                    index = newParent.getChildCount();
                }

                model.moveNode(draggedNode, newParent, index);

                return true;
            } catch (Exception ex) {
                UIUtils.showError(ex, getParent());

                return false;
            }
        }
    }

    public static class DefaultGameConfigChangeListener
        implements GameConfigChangeListener
    {
        private final GameConfig config;

        public DefaultGameConfigChangeListener(GameConfig config) {
            this.config = config;
        }

        /**
         * Called before the move happens.
         * Throw an exception to veto the change.
         */
        public void beforeMove(MoveEvent event)
            throws Exception
        {
            System.out.println("Moving " + event.moved + " with parent " + event.oldParent + " from index " + event.oldIndex + " to new parent " + event.newParent + " with index " + event.newIndex);

            // Figure out what we are moving.
            //
            // Moving Sections is easy: we are always re-ordering the
            // list of sections within the entire GameConfig.
            // So this is just an exercise in re-ordering the Sections
            // List.

            if(event.moved instanceof Section) {
                // Make a copy of the sections to mutate
                ArrayList<Section> sections = new ArrayList<>(config.getSections());

System.out.println("Current section order (from config): " + sections.stream().map(Section::getName).collect(Collectors.toList()));
                Section movingSection = sections.get(event.oldIndex);
                // Verify we are moving the thing we think we are moving
                if(!movingSection.equals(event.moved)) {
                    throw new IllegalStateException("Expected section " + ((Section)event.moved).getName() + " at position " + event.oldIndex + ", but got " + movingSection.getName() + " instead");
                }

                int index = event.newIndex;
                // If we are moving the section DOWN, then we need to
                // decrease the index to account for the section being
                // removed before adding it back.
                if(index > event.oldIndex) {
                    index -= 1;
                }
                sections.remove(movingSection);
                sections.add(index, movingSection);

                config.setSections(sections);
System.out.println("New section order: " + sections.stream().map(Section::getName).collect(Collectors.toList()));
            } else if(event.moved instanceof Field) {
                // Fields can be moved within their own sections, or
                // between sections.
                //
                // Figure out which we are dealing with.
                //
                if(Objects.equals(event.oldParent, event.newParent)) {
                    // We are moving a field within the same section.
                    // Just re-order.
                    List<Field> fields = ((Section)event.oldParent).getFields();

                    Field movingField = fields.get(event.oldIndex);

                    // Verify we are moving the thing we think we are moving
                    if(!movingField.equals(event.moved)) {
                        throw new IllegalStateException("Expected field " + ((Field)event.moved).getTitle() + " at position " + event.oldIndex + ", but got " + movingField.getTitle() + " instead");
                    }

                    int index = event.newIndex;
                    // If we are moving the section DOWN, then we need to
                    // decrease the index to account for the section being
                    // removed before adding it back.
                    if(index > event.oldIndex) {
                        index -= 1;
                    }
                    fields.remove(movingField);
                    fields.add(index, movingField);

                    System.out.println("New field order: " + fields.stream().map(Field::getTitle).collect(Collectors.toList()));
                } else {
                    Section oldSection = (Section)event.oldParent;
                    Section newSection = (Section)event.newParent;

System.out.println("Moving field " + event.moved + " to new section: " + newSection);

                    // Verify we are moving what we think we are moving
                    List<Field> fields = oldSection.getFields();
                    Field movingField = fields.get(event.oldIndex);
                    if(!movingField.equals(event.moved)) {
                        throw new IllegalStateException("Expected field " + ((Field)event.moved).getTitle() + " at position " + event.oldIndex + ", but got " + movingField.getTitle() + " instead");
                    }

                    fields.remove(movingField);
                    oldSection.setFields(fields);

                    fields = newSection.getFields();
                    fields.add(event.newIndex, movingField);
                    newSection.setFields(fields);
                }
            } else {
                System.out.println("*** IGNORING DROP EVENT ");
            }
        }

        /** Called after the move has been applied */
        public void afterMove(MoveEvent event) {
            // Do nothing
        }
    }

    private void editSelectedNode(JTree tree) {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return;
        }

        Object last = path.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) {
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) last;

        Object userObject = node.getUserObject();

        if(userObject instanceof Section) {
            final Section existingSection = (Section)userObject;
            final Section newSection = new Section();
            newSection.setName(existingSection.getName());

            SectionEditorDialog dialog = new SectionEditorDialog(this, newSection);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                // Notify the tree that the node changed
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

                existingSection.setName(newSection.getName());
                model.nodeChanged(node);

                // TODO: save the game config?
            }
        } else if(userObject instanceof Field) {
            final Field existingField = (Field)userObject;
            final Field newField = new Field();

            existingField.copyTo(newField);

            FieldEditorDialog dialog = new FieldEditorDialog(this, newField);
            dialog.setVisible(true);

            if (dialog.isConfirmed()) {
                // Notify the tree that the node changed
                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();

                newField.copyTo(existingField);

                model.nodeChanged(node);

                // TODO: save the game config?
            }
        }
    }

    public static class SavingGameConfigChangeListener
        implements GameConfigChangeListener
    {
        private final GameConfig config;
        private final File file;
        private final boolean prettyPrint;

        public SavingGameConfigChangeListener(GameConfig config,
                                              File file,
                                              boolean prettyPrint) {
            this.config = config;
            this.file = file;
            this.prettyPrint = prettyPrint;
        }

        /**
         * Called before the move happens.
         * Throw an exception to veto the change.
         */
        public void beforeMove(MoveEvent event)
            throws Exception
        {
            config.saveToFile(getFile(), getPrettyPrint());
        }

        @Override
        public void afterMove(MoveEvent event) {
        }

        public File getFile() {
            return file;
        }

        public boolean getPrettyPrint() {
            return prettyPrint;
        }
    }

    public static void usage(PrintStream out) {
        out.println("Usage: " + GameConfigEditorDialog.class.getName() + " <jsonfile> [savetofile]");
        out.println();
        out.println("Options");
    }

    public static void main(String[] args)
        throws Exception
    {
        if(0 == args.length) {
            usage(System.err);

            System.exit(1);
        }

        GameConfig config = GameConfig.readFile(new File(args[0]));
        File outputFile = null;
        if(args.length > 1) {
            outputFile = new File(args[1]);
        }
        GameConfigChangeListener pcl = new DefaultGameConfigChangeListener(config);

        GameConfigEditorDialog gced = new GameConfigEditorDialog(null, config, pcl);
        if(null != outputFile) {
            gced.addGameConfigChangeListener(new SavingGameConfigChangeListener(config, outputFile, true));
        }
        gced.setVisible(true);
    }
}
