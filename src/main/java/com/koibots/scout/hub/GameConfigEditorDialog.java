package com.koibots.scout.hub;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Objects;
import javax.swing.DropMode;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import com.koibots.scout.hub.GameConfig.Field;
import com.koibots.scout.hub.GameConfig.Section;

public class GameConfigEditorDialog
    extends EditorDialog<GameConfig>
{
    private static final long serialVersionUID = -716394164351569605L;

    private GameConfigTreeModel model;

    public GameConfigEditorDialog(Frame owner,
                                  GameConfig config)
    {
        super(owner, "Edit Game", config);

        model = new GameConfigTreeModel(config);

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

        add(createButtonPanel(new JButton("Save")), BorderLayout.SOUTH);

        setSize(400, 500);
        setLocationRelativeTo(owner);
    }

    @Override
    protected void applyChanges(GameConfig config) {
        //
        // Copy mutated GameConfig from the table model
        // back into the GameConfig
        //
        final DefaultMutableTreeNode root = (DefaultMutableTreeNode)model.getRoot();

        final int sectionCount = root.getChildCount();

        ArrayList<Section> sections = new ArrayList<>(sectionCount);

        // Copy each section
        for(int i=0; i<sectionCount; ++i) {
            final DefaultMutableTreeNode node = (DefaultMutableTreeNode)root.getChildAt(i);
            Object userObject = node.getUserObject();
            if(!(userObject instanceof Section)) {
                throw new IllegalStateException("Expected Section, got " + (null == userObject ? "null" : userObject.getClass()) + " instead");
            }

            Section modelSection = (Section)userObject;

            // Copy each section's fields
            final int fieldCount = node.getChildCount();

            ArrayList<Field> fields = new ArrayList<>(fieldCount);
            for(int j=0; j<fieldCount; ++j) {
                final DefaultMutableTreeNode fieldNode = (DefaultMutableTreeNode)node.getChildAt(j);
                userObject = fieldNode.getUserObject();

                if(!(userObject instanceof Field)) {
                    throw new IllegalStateException("Expected Field, got " + (null == userObject ? "null" : userObject.getClass()) + " instead");
                }
                Field modelField = (Field)userObject;

                Field field = new Field();
                modelField.copyTo(field);
                fields.add(field);
            }

            Section section = new Section();
            section.setName(modelSection.getName());
            section.setFields(fields);
            sections.add(section);
        }

        config.setSections(sections);
    }

    private class GameConfigTreeModel extends DefaultTreeModel {

        private static final long serialVersionUID = 7909699472552723977L;

        public GameConfigTreeModel(GameConfig config) {
            super(buildRoot(config));
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

            // apply to tree
            System.out.println("Inserting node " + node + " into " + newParent + " at index " + index);
            removeNodeFromParent(node);

            if(Objects.equals(oldParent, newParent) && oldIndex < index) {
                index -= 1;
            }

            insertNodeInto(node, newParent, index);
        }
    }

    private class GameConfigTransferHandler extends TransferHandler {

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
            }
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

        GameConfigEditorDialog gced = new GameConfigEditorDialog(null, config);

        gced.setVisible(true);

        if(gced.isConfirmed()) {
            if(null != outputFile) {
                gced.getUserObject().saveToFile(outputFile, true);
            } else {
                System.out.println("Configuration saved:");
                OutputStreamWriter out = new OutputStreamWriter(System.out);
                gced.getUserObject().saveTo(out, true);
                out.flush();
            }
        } else {
            System.out.println("Cancelled");
        }
    }
}
