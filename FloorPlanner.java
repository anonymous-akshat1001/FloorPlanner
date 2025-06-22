import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import javax.swing.*;

public class FloorPlanner extends JFrame {
    private JPanel canvas;
    private JPanel controlPanel;
    private JPanel propertiesPanel;
    private ArrayList<Room> rooms;
    private static final Color GRID_COLOR = Color.BLACK;
    private static final int GRID_SIZE = 20;
    private Room selectedRoom;
    private int nextRoomX = GRID_SIZE;
    private int nextRoomY = GRID_SIZE;
    private JFileChooser fileChooser; // New field for file operations
    private JComboBox<String> directionComboBox; // New field for direction selection
    private JButton deleteRoomButton; // New field for delete button
    private JPanel fixturesPanel;
    private Fixture selectedFixture;
    private Fixture draggedFixture;
    private Point draggedFixtureStart;
    private Point originalFixturePosition;
    private Furniture selectedFurniture;
    private Furniture draggedFurniture;
    private Point draggedFurnitureStart;
    private Point originalFurniturePosition;
    private JPanel furniturePanel;
    private ArrayList<Door> doors = new ArrayList<>();
    private ArrayList<Window> windows = new ArrayList<>();
    private boolean addingDoor = false;
    private boolean addingWindow = false;
    private static final int DOOR_WIDTH = GRID_SIZE * 2;
    private Point firstRoomClick = null;
    private Room firstSelectedRoom = null;

    // Add Door and Window classes
    private static class Door implements Serializable {
        private static final long serialVersionUID = 1L;
        Point start;
        Point end;
        Room room1;
        Room room2;

        public Door(Point start, Point end, Room room1, Room room2) {
            this.start = start;
            this.end = end;
            this.room1 = room1;
            this.room2 = room2;
        }

        public boolean overlaps(Door other) {
            return start.distance(other.start) < DOOR_WIDTH || 
                   start.distance(other.end) < DOOR_WIDTH ||
                   end.distance(other.start) < DOOR_WIDTH ||
                   end.distance(other.end) < DOOR_WIDTH;
        }

        public boolean overlaps(Window window) {
            return start.distance(window.position) < DOOR_WIDTH ||
                   end.distance(window.position) < DOOR_WIDTH;
        }
    }

    private static class Window implements Serializable {
        private static final long serialVersionUID = 1L;
        Point position;
        Room room;
        boolean isHorizontal;

        public Window(Point position, Room room, boolean isHorizontal) {
            this.position = position;
            this.room = room;
            this.isHorizontal = isHorizontal;
        }

        public Rectangle getBounds() {
            if (isHorizontal) {
                return new Rectangle(position.x - DOOR_WIDTH/2, position.y - 5, DOOR_WIDTH, 10);
            } else {
                return new Rectangle(position.x - 5, position.y - DOOR_WIDTH/2, 10, DOOR_WIDTH);
            }
        }
    }


    private JPanel createFurniturePanel() {
        furniturePanel = new JPanel();
        furniturePanel.setLayout(new BoxLayout(furniturePanel, BoxLayout.Y_AXIS));
        furniturePanel.setBorder(BorderFactory.createTitledBorder("Furniture"));
        String[][] furnitureTypes = {
            {"Bed", "images/bed.png"}, {"Chair", "images/chair.png"}, {"Table", "images/table.png"}, {"Sofa", "images/sofa.png"}, {"Dining Set", "images/dining set.png"}
        };
        for (String[] furnitureType : furnitureTypes) {
            JButton furnitureButton = new JButton(furnitureType[0]);
            furnitureButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 
                furnitureButton.getPreferredSize().height));
            furnitureButton.setTransferHandler(new FurnitureTransferHandler(furnitureType[0], furnitureType[1]));
            furnitureButton.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    JButton button = (JButton) e.getSource();
                    TransferHandler handler = button.getTransferHandler();
                    handler.exportAsDrag(button, e, TransferHandler.COPY);
                }
            });
            furniturePanel.add(furnitureButton);
            furniturePanel.add(Box.createVerticalStrut(5));
        }
        JButton rotateFurnitureButton = new JButton("Rotate");
        rotateFurnitureButton.addActionListener(e -> rotateSelectedFurniture());
        JButton deleteFurnitureButton = new JButton("Delete");
        deleteFurnitureButton.addActionListener(e -> deleteSelectedFurniture());
        furniturePanel.add(rotateFurnitureButton);
        furniturePanel.add(deleteFurnitureButton);
        return furniturePanel;
    }
    private void rotateSelectedFurniture() {
        if (selectedFurniture != null) {
            selectedFurniture.rotate();
            canvas.repaint();
        }
    }
    private void deleteSelectedFurniture() {
        if (selectedFurniture != null && selectedFurniture.getParentRoom() != null) {
            selectedFurniture.getParentRoom().removeFurniture(selectedFurniture);
            selectedFurniture = null;
            canvas.repaint();
        }
    }
    private class FurnitureTransferHandler extends TransferHandler {
        private String furnitureType; private String imagePath;
        public FurnitureTransferHandler(String type, String path) {
            this.furnitureType = type; this.imagePath = path;
        }
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection("FURNITURE:" + furnitureType + "," + imagePath);
        }
    }
    private JPanel createFixturesPanel() {
        fixturesPanel = new JPanel();
        fixturesPanel.setLayout(new BoxLayout(fixturesPanel, BoxLayout.Y_AXIS));
        fixturesPanel.setBorder(BorderFactory.createTitledBorder("Fixtures"));
        String[][] fixtureTypes = {
            {"Commode", "images/commode.png"}, {"Washbasin", "images/washbasin.png"}, {"Shower", "images/shower.png"}, {"Kitchen Sink", "images/kitchen sink.png"}, {"Stove", "images/stove.png"}, {"Door", "images/door.png"}, {"Window", "images/window.png"}
        };
        for (String[] fixtureType : fixtureTypes) {
            JButton fixtureButton = new JButton(fixtureType[0]);
            fixtureButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixtureButton.getPreferredSize().height));
            fixtureButton.setTransferHandler(new FixtureTransferHandler(fixtureType[0], fixtureType[1]));
            fixtureButton.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    JButton button = (JButton) e.getSource();
                    TransferHandler handler = button.getTransferHandler();
                    handler.exportAsDrag(button, e, TransferHandler.COPY);
                }
            });
            fixturesPanel.add(fixtureButton);
            fixturesPanel.add(Box.createVerticalStrut(5));
        }
        return fixturesPanel;
    }
    private class FixtureTransferHandler extends TransferHandler {
        private String fixtureType;
        private String imagePath;
        public FixtureTransferHandler(String type, String path) {
            this.fixtureType = type;
            this.imagePath = path;
        }
        @Override
        public int getSourceActions(JComponent c) {
            return COPY;
        }
        @Override
        protected Transferable createTransferable(JComponent c) {
            return new StringSelection("FURNITURE:" + fixtureType + "," + imagePath);
        }
    }
    private Room draggedRoom;
    private Point dragStart;
    private Rectangle originalBounds;
    public FloorPlanner() {
        super("Floor Plan Builder");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        rooms = new ArrayList<>();
        fileChooser = new JFileChooser(); // Initialize file chooser
        initializeUI();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setVisible(true);
    }
    private void initializeUI() {
        setLayout(new BorderLayout());
        JToolBar toolbar = createToolbar();
        add(toolbar, BorderLayout.NORTH);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        canvas = new DrawingCanvas();
        controlPanel = createControlPanel();
        splitPane.setLeftComponent(new JScrollPane(canvas));
        splitPane.setRightComponent(new JScrollPane(controlPanel));
        splitPane.setResizeWeight(0.75);
        add(splitPane, BorderLayout.CENTER);
    }
    private JToolBar createToolbar() {
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton openBtn = new JButton("Open");
        JButton saveBtn = new JButton("Save");
        // Add action listeners for save and open buttons
        saveBtn.addActionListener(e -> saveFloorPlan());
        openBtn.addActionListener(e -> loadFloorPlan());
        toolbar.add(openBtn);
        toolbar.add(saveBtn);
        return toolbar;
    }
    private JPanel createControlPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    JPanel roomsPanel = new JPanel();
    roomsPanel.setLayout(new BoxLayout(roomsPanel, BoxLayout.Y_AXIS));
    roomsPanel.setBorder(BorderFactory.createTitledBorder("Rooms"));
    String[][] roomTypes = {
            { "Add Bedroom", "GREEN" },
            { "Add Kitchen", "RED" },
            { "Add Drawing Room", "YELLOW" },
            { "Add Bathroom", "BLUE" }
    };
    for (String[] roomType : roomTypes) {
        JButton button = new JButton(roomType[0]);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, button.getPreferredSize().height));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.addActionListener(e -> {
            addRoom(roomType[0], Color.decode(getColorHex(roomType[1])));
            directionComboBox.setSelectedItem("None"); // Reset direction after adding room
        });
        roomsPanel.add(button);
        roomsPanel.add(Box.createVerticalStrut(5));
    }
    deleteRoomButton = new JButton("Delete Selected Room");
    deleteRoomButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, deleteRoomButton.getPreferredSize().height));
    deleteRoomButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    deleteRoomButton.setEnabled(false); // Initially disabled
    deleteRoomButton.addActionListener(e -> deleteSelectedRoom());
    roomsPanel.add(Box.createVerticalStrut(10));
    roomsPanel.add(deleteRoomButton);
    JPanel directionPanel = new JPanel();
    directionPanel.setLayout(new BoxLayout(directionPanel, BoxLayout.Y_AXIS));
    directionPanel.setBorder(BorderFactory.createTitledBorder("Placement Direction"));
    String[] directions = { "None", "North", "East", "South", "West" };
    directionComboBox = new JComboBox<>(directions);
    directionComboBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, directionComboBox.getPreferredSize().height));
    directionComboBox.setAlignmentX(Component.CENTER_ALIGNMENT);
    directionPanel.add(directionComboBox);
    // Properties Section
    propertiesPanel = new JPanel();
    propertiesPanel.setLayout(new BoxLayout(propertiesPanel, BoxLayout.Y_AXIS));
    propertiesPanel.setBorder(BorderFactory.createTitledBorder("Properties"));
    JLabel propertiesLabel = new JLabel("Select a room to view properties");
    propertiesLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    propertiesPanel.add(propertiesLabel);
    // Create and add Fixtures Panel
    JPanel fixturesPanel = createFixturesPanel();
    JPanel furniturePanel = createFurniturePanel();
    // Add all panels to main control panel
    panel.add(roomsPanel);
    panel.add(Box.createVerticalStrut(10));
    
    JButton addDoorButton = new JButton("Add Door");
        addDoorButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, addDoorButton.getPreferredSize().height));
        addDoorButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addDoorButton.addActionListener(e -> {
            addingDoor = !addingDoor;
            addingWindow = false;
            addDoorButton.setBackground(addingDoor ? Color.LIGHT_GRAY : null);
            firstRoomClick = null;
            firstSelectedRoom = null;
        });

        JButton addWindowButton = new JButton("Add Window");
        addWindowButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, addWindowButton.getPreferredSize().height));
        addWindowButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        addWindowButton.addActionListener(e -> {
            addingWindow = !addingWindow;
            addingDoor = false;
            addWindowButton.setBackground(addingWindow ? Color.LIGHT_GRAY : null);
        });

    
    panel.add(fixturesPanel);  // Add fixtures panel
    panel.add(Box.createVerticalStrut(10));
    panel.add(furniturePanel);  // Add fixtures panel
    panel.add(Box.createVerticalStrut(10)); panel.add(directionPanel); panel.add(Box.createVerticalStrut(10)); panel.add(propertiesPanel);
    

    // fixturesPanel.add(addDoorButton);
    // fixturesPanel.add(Box.createVerticalStrut(5));
    // fixturesPanel.add(addWindowButton);
    // fixturesPanel.add(Box.createVerticalStrut(5));


    return panel;
}   


private void deleteSelectedRoom() {
        if (selectedRoom != null) {
            rooms.remove(selectedRoom);
            selectedRoom = null;
            selectedFixture = null; // Clear selected fixture as well
            updatePropertiesPanel(null);
            deleteRoomButton.setEnabled(false);
            canvas.repaint();
        }
    }   private String getColorHex(String colorName) {
        return switch (colorName) {
            case "GREEN" -> "#90EE90";
            case "RED" -> "#FFB6C1";
            case "YELLOW" -> "#FFFFE0";
            case "BLUE" -> "#ADD8E6";
            default -> "#FFFFFF";
        };
    } private Point findAvailableSpace(int width, int height) {
        if (selectedRoom == null || directionComboBox.getSelectedItem().equals("None")) {
            // Use existing logic for default placement
            return findDefaultAvailableSpace(width, height);
        }
        String direction = (String) directionComboBox.getSelectedItem();
        Point position = calculatePositionInDirection(selectedRoom.bounds, width, height, direction);
        // First check if the position would be outside canvas bounds
        if (position != null) {
            Rectangle candidateRect = new Rectangle(position.x, position.y, width, height);
            if (!isWithinCanvas(candidateRect)) {
                JOptionPane.showMessageDialog(this,
                        "Cannot place room - it would extend beyond the canvas boundaries!",
                        "Boundary Error",
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
            // Then check for overlaps
            for (Room room : rooms) {
                if (room.bounds.intersects(candidateRect)) {
                    JOptionPane.showMessageDialog(this,
                            "Cannot place room in " + direction + " direction - overlap detected!",
                            "Overlap Error",
                            JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        }
        return position;
    }
    private boolean isWithinCanvas(Rectangle rect) {
        return rect.x >= 0 &&
                rect.y >= 0 &&
                rect.x + rect.width <= canvas.getWidth() &&
                rect.y + rect.height <= canvas.getHeight();
    }
    // New helper method for directional placement
    private Point calculatePositionInDirection(Rectangle reference, int width, int height, String direction) {
        return switch (direction) {
            case "North" -> new Point(reference.x, reference.y - height);
            case "South" -> new Point(reference.x, reference.y + reference.height);
            case "East" -> new Point(reference.x + reference.width, reference.y);
            case "West" -> new Point(reference.x - width, reference.y);
            default -> null;
        }; // Removed GRID_SIZE
    }
    private Point findDefaultAvailableSpace(int width, int height) {
        // First check if the room is too big for the canvas
        if (width > canvas.getWidth() || height > canvas.getHeight()) {
            JOptionPane.showMessageDialog(this,
                    "Room dimensions are too large for the canvas!",
                    "Size Error",
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        Point candidate = new Point(GRID_SIZE, GRID_SIZE);
        boolean foundSpace = false;
        int maxAttempts = 1000;
        int attempts = 0;
        while (!foundSpace && attempts < maxAttempts) {
            Rectangle candidateRect = new Rectangle(candidate.x, candidate.y, width, height);
            boolean hasOverlap = false;
            // Check if the candidate position is within canvas bounds
            if (!isWithinCanvas(candidateRect)) {
                JOptionPane.showMessageDialog(this,
                        "Not enough space on the canvas to add this room!",
                        "Space Error",
                        JOptionPane.ERROR_MESSAGE);
                return null;
            }
            for (Room room : rooms) {
                if (room.bounds.intersects(candidateRect)) {
                    hasOverlap = true;
                    break;
                }
            }
            if (!hasOverlap) {
                foundSpace = true;
            } else {
                candidate.x += GRID_SIZE;
                if (candidate.x + width > canvas.getWidth()) {
                    candidate.x = GRID_SIZE;
                    candidate.y += GRID_SIZE;
                }
                if (candidate.y + height > canvas.getHeight()) {
                    // Instead of automatically expanding canvas, show error
                    JOptionPane.showMessageDialog(this,
                            "Not enough space on the canvas to add this room!",
                            "Space Error",
                            JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
            attempts++;
        }
        return candidate;
    }
    private void addRoom(String roomType, Color color) {
        JTextField widthField = new JTextField(5);
        JTextField heightField = new JTextField(5);
        JPanel panel = new JPanel();
        panel.add(new JLabel("Width (m):"));
        panel.add(widthField);
        panel.add(new JLabel("Height (m):"));
        panel.add(heightField);
        int result = JOptionPane.showConfirmDialog(null, panel,
                "Enter " + roomType + " Dimensions", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                int width = (int) (Double.parseDouble(widthField.getText()) * GRID_SIZE * 2);
                int height = (int) (Double.parseDouble(heightField.getText()) * GRID_SIZE * 2);

                // Find available space for the new room
                Point availableSpace = findAvailableSpace(width, height);

                if (availableSpace != null) {
                    Room newRoom = new Room(availableSpace.x, availableSpace.y, width, height, color, roomType);
                    rooms.add(newRoom);
                    canvas.repaint();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null,
                        "Please enter valid numbers for dimensions.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    private void updatePropertiesPanel(Room room) {
        propertiesPanel.removeAll();
        if (room != null) {
            double xMeters = room.bounds.x * 0.5 / GRID_SIZE;
            double yMeters = room.bounds.y * 0.5 / GRID_SIZE;
            double widthMeters = room.bounds.width * 0.5 / GRID_SIZE;
            double heightMeters = room.bounds.height * 0.5 / GRID_SIZE;
            JLabel typeLabel = new JLabel("Type: " + room.type);
            JLabel positionLabel = new JLabel(String.format("Position: (%.2f m, %.2f m)", xMeters, yMeters));
            JLabel dimensionsLabel = new JLabel(
                    String.format("Dimensions: %.2f m × %.2f m", widthMeters, heightMeters));
            JLabel areaLabel = new JLabel(String.format("Area: %.2f m²", widthMeters * heightMeters));
            propertiesPanel.add(typeLabel);
            propertiesPanel.add(Box.createVerticalStrut(5));
            propertiesPanel.add(positionLabel);
            propertiesPanel.add(Box.createVerticalStrut(5));
            propertiesPanel.add(dimensionsLabel);
            propertiesPanel.add(Box.createVerticalStrut(5));
            propertiesPanel.add(areaLabel);
            // Enable delete button when a room is selected
            deleteRoomButton.setEnabled(true);
        } else {
            propertiesPanel.add(new JLabel("Select a room to view properties"));
            // Disable delete button when no room is selected
            deleteRoomButton.setEnabled(false);
        }
        propertiesPanel.revalidate();
        propertiesPanel.repaint();
    }
    // New method to save the floor plan
    private void saveFloorPlan() {
        fileChooser
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Floor Plan Files (*.fpl)", "fpl"));
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            // Add .fpl extension if not present
            if (!file.getName().toLowerCase().endsWith(".fpl")) {
                file = new File(file.getPath() + ".fpl");
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
                // Create a serializable data structure to store room information
                ArrayList<RoomData> roomDataList = new ArrayList<>();
                for (Room room : rooms) {
                    RoomData roomData = new RoomData(
                            room.bounds.x,
                            room.bounds.y,
                            room.bounds.width,
                            room.bounds.height,
                            room.color,
                            room.type);
                    roomDataList.add(roomData);
                }

                oos.writeObject(roomDataList);
                JOptionPane.showMessageDialog(this,
                        "Floor plan saved successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error saving floor plan: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // New method to load the floor plan
    private void loadFloorPlan() {
        fileChooser
                .setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Floor Plan Files (*.fpl)", "fpl"));
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileChooser.getSelectedFile()))) {
                // Read the room data list
                ArrayList<RoomData> roomDataList = (ArrayList<RoomData>) ois.readObject();

                // Clear existing rooms
                rooms.clear();

                // Recreate rooms from the loaded data
                for (RoomData roomData : roomDataList) {
                    Room room = new Room(
                            roomData.x,
                            roomData.y,
                            roomData.width,
                            roomData.height,
                            roomData.color,
                            roomData.type);
                    rooms.add(room);
                }
                // Reset selection and repaint
                selectedRoom = null;
                updatePropertiesPanel(null);
                canvas.repaint();
                JOptionPane.showMessageDialog(this,
                        "Floor plan loaded successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException | ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(this,
                        "Error loading floor plan: " + ex.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    class Furniture implements Serializable {
        private static final long serialVersionUID = 1L;
        private Point position;
        private ImageIcon image;
        private String type;
        private int rotation; // 0, 90, 180, 270 degrees
        private Rectangle bounds;
        private Room parentRoom;
        private static final int FURNITURE_SIZE = 40; // 3 grid cells (20px * 3) - larger than fixtures
        
        public Furniture(String type, Point position, String imagePath, Room parentRoom) {
            this.type = type;
            this.position = position;
            this.rotation = 0;
            this.parentRoom = parentRoom;
            try {
                ImageIcon originalIcon = new ImageIcon(imagePath);
                Image scaledImage = originalIcon.getImage().getScaledInstance(FURNITURE_SIZE, FURNITURE_SIZE, Image.SCALE_SMOOTH);
                this.image = new ImageIcon(scaledImage);
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateBounds();
        }
    
        public void updatePosition(int dx, int dy) {
            this.position.x += dx;
            this.position.y += dy;
            updateBounds();
        }
    
        private void updateBounds() {
            this.bounds = new Rectangle(position.x, position.y, FURNITURE_SIZE, FURNITURE_SIZE);
        }
    
        public void setPosition(Point position) {
            this.position = position;
            updateBounds();
        }
    
        public void rotate() {
            rotation = (rotation + 90) % 360;
        }
    
        public void draw(Graphics g) {
            if (image != null) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.translate(position.x + FURNITURE_SIZE/2, position.y + FURNITURE_SIZE/2);
                g2d.rotate(Math.toRadians(rotation));
                g2d.drawImage(image.getImage(), -FURNITURE_SIZE/2, -FURNITURE_SIZE/2, null);
                g2d.rotate(-Math.toRadians(rotation));
                g2d.translate(-(position.x + FURNITURE_SIZE/2), -(position.y + FURNITURE_SIZE/2));
            }
        }
    
        public boolean intersects(Furniture other) {
            return bounds.intersects(other.bounds);
        }
    
        public boolean intersectsFixture(Fixture fixture) {
            return bounds.intersects(fixture.getBounds());
        }
    
        public Rectangle getBounds() {
            return bounds;
        }
    
        public Room getParentRoom() {
            return parentRoom;
        }
    
        public void setParentRoom(Room room) {
            this.parentRoom = room;
        }
    
        public String getType() {
            return type;
        }
    }

    class Fixture implements Serializable {
        private static final long serialVersionUID = 1L;
        private Point position;
        private ImageIcon image;
        private String type;
        private int rotation; private Rectangle bounds;private Room parentRoom;private static final int FIXTURE_SIZE = 40; // 2 grid cells (20px * 2)
        public Fixture(String type, Point position, String imagePath, Room parentRoom) {
            this.type = type;
            this.position = position;
            this.rotation = 0;
            this.parentRoom = parentRoom;
            try {
                ImageIcon originalIcon = new ImageIcon(imagePath);
                Image scaledImage = originalIcon.getImage().getScaledInstance(FIXTURE_SIZE, FIXTURE_SIZE, Image.SCALE_SMOOTH);
                this.image = new ImageIcon(scaledImage);
            } catch (Exception e) {
                e.printStackTrace();
            }
            updateBounds();
        }
        public void updatePosition(int dx, int dy) {
            this.position.x += dx;
            this.position.y += dy;
            updateBounds();
        }
        public boolean intersectsFurniture(Furniture furniture) {
            return bounds.intersects(furniture.getBounds());
        } private void updateBounds() {
            this.bounds = new Rectangle(position.x, position.y, FIXTURE_SIZE, FIXTURE_SIZE);
        }public void setPosition(Point position) {
            this.position = position;
            updateBounds();
        }
        public void rotate() {
            rotation = (rotation + 90) % 360;
        }
        public void draw(Graphics g) {
            if (image != null) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.translate(position.x + FIXTURE_SIZE/2, position.y + FIXTURE_SIZE/2);
                g2d.rotate(Math.toRadians(rotation));
                g2d.drawImage(image.getImage(), -FIXTURE_SIZE/2, -FIXTURE_SIZE/2, null);
                g2d.rotate(-Math.toRadians(rotation));
                g2d.translate(-(position.x + FIXTURE_SIZE/2), -(position.y + FIXTURE_SIZE/2));
            }
        }
        public boolean intersects(Fixture other) {
            return bounds.intersects(other.bounds);
        }
        public Rectangle getBounds() {
            return bounds;
        }
        public Room getParentRoom() {
            return parentRoom;
        }
        public void setParentRoom(Room room) {
            this.parentRoom = room;
        }
        public String getType() {
            return type;
        }
    }
    // New serializable class to store room data
    private static class RoomData implements Serializable {
        private static final long serialVersionUID = 1L;
        int x, y, width, height;
        Color color;
        String type;
        public RoomData(int x, int y, int width, int height, Color color, String type) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
            this.type = type;
        }
    }

    // Add a new serializable class to store item data
private static class ItemData implements Serializable {
    private static final long serialVersionUID = 1L;

    int x, y, width, height;
    String type;
    String category;
    int rotation;
    int parentRoomIndex; // Store room index instead of reference
    Point relativePosition;

    public ItemData(int x, int y, int width, int height, String type, String category, 
                   int rotation, int parentRoomIndex, Point relativePosition) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
        this.category = category;
        this.rotation = rotation;
        this.parentRoomIndex = parentRoomIndex;
        this.relativePosition = relativePosition;
    }
}

    private class DrawingCanvas extends JPanel {
        public DrawingCanvas() {
            setBackground(new Color(204,204,204));
            setPreferredSize(new Dimension(800, 600));
            setTransferHandler(new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport support) {
                    return support.isDataFlavorSupported(DataFlavor.stringFlavor);
                }

                @Override
                public boolean importData(TransferSupport support) {
                    try {
                        String data = (String) support.getTransferable()
                            .getTransferData(DataFlavor.stringFlavor);
                        
                        if (data.startsWith("FURNITURE:")) {
                            // Handle furniture drop
                            String[] parts = data.substring(10).split(",");
                            Point dropPoint = support.getDropLocation().getDropPoint();
                            
                            // Snap to grid
                            int x = Math.round(dropPoint.x / (float) GRID_SIZE) * GRID_SIZE;
                            int y = Math.round(dropPoint.y / (float) GRID_SIZE) * GRID_SIZE;
                            
                            // Find target room
                            Room targetRoom = null;
                            for (Room room : rooms) {
                                if (room.bounds.contains(x, y)) {
                                    targetRoom = room;
                                    break;
                                }
                            }

                            if (targetRoom != null) {
                                Furniture newFurniture = new Furniture(parts[0], new Point(x, y), 
                                    parts[1], targetRoom);
                                
                                // Check overlaps with existing furniture and fixtures
                                boolean hasOverlap = false;
                                for (Furniture existing : targetRoom.getFurniture()) {
                                    if (newFurniture.intersects(existing)) {
                                        hasOverlap = true;
                                        break;
                                    }
                                }
                                
                                for (Fixture fixture : targetRoom.getFixtures()) {
                                    if (newFurniture.intersectsFixture(fixture)) {
                                        hasOverlap = true;
                                        break;
                                    }
                                }

                                if (!hasOverlap) {
                                    targetRoom.addFurniture(newFurniture);
                                    repaint();
                                    return true;
                                } else {
                                    JOptionPane.showMessageDialog(null,
                                        "Cannot place furniture here - overlap detected!",
                                        "Overlap Error",
                                        JOptionPane.ERROR_MESSAGE);
                                }
                            }
                        } else if (data.startsWith("FIXTURE:")) {
                    String[] parts = data.split(",");
                    Point dropPoint = support.getDropLocation().getDropPoint();
                    int x = Math.round(dropPoint.x / (float) GRID_SIZE) * GRID_SIZE;
                    int y = Math.round(dropPoint.y / (float) GRID_SIZE) * GRID_SIZE;
                    Room targetRoom = null;
                    for (Room room : rooms) {
                        if (room.bounds.contains(x, y)) {
                            targetRoom = room;
                            break;
                        }
                    }
                    if (targetRoom != null) {
                        Fixture newFixture = new Fixture(parts[0], new Point(x, y), parts[1], targetRoom);
                        
                        // Check for overlap with existing fixtures
                        boolean hasOverlap = false;
                        for (Fixture existing : targetRoom.getFixtures()) {
                            if (newFixture.intersects(existing)) {
                                hasOverlap = true;
                                break;
                            }
                        }

                        if (!hasOverlap) {
                            targetRoom.addFixture(newFixture);
                            repaint();
                            return true;
                        } else {
                            JOptionPane.showMessageDialog(null,
                                "Cannot place fixture here - overlap detected!",
                                "Overlap Error",
                                JOptionPane.ERROR_MESSAGE);
                        }
                    }
                        }}
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                }
            });
            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleMousePressed(e);
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    handleMouseReleased();
                }
                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMouseDragged(e);
                }
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (draggedRoom == null) {
                        handleRoomSelection(e.getX(), e.getY());
                    }
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }
        private void handleMousePressed(MouseEvent e) {
            Point clickPoint = e.getPoint();
        selectedFurniture = null;
        draggedFurniture = null;
        for (Room room : rooms) {
            for (Furniture furniture : room.getFurniture()) {
                if (furniture.getBounds().contains(clickPoint)) {
                    draggedFurniture = furniture;
                    selectedFurniture = furniture;
                    draggedFurnitureStart = new Point(
                        clickPoint.x - furniture.getBounds().x,
                        clickPoint.y - furniture.getBounds().y
                    );
                    originalFurniturePosition = new Point(
                        furniture.getBounds().x,
                        furniture.getBounds().y
                    );
                    repaint();
                    return;  // Exit the method after finding furniture
                }
            }
        }
        selectedFixture = null;
        draggedFixture = null;
        for (Room room : rooms) {
            for (Fixture fixture : room.getFixtures()) {
                if (fixture.getBounds().contains(clickPoint)) {
                    draggedFixture = fixture;
                    selectedFixture = fixture;
                    draggedFixtureStart = new Point(
                        clickPoint.x - fixture.getBounds().x,
                        clickPoint.y - fixture.getBounds().y
                    );
                    originalFixturePosition = new Point(
                        fixture.getBounds().x,
                        fixture.getBounds().y
                    );
                    repaint();
                    return;  // Exit the method after finding a fixture
                }
            }
        }
        for (Room room : rooms) {
            if (room.bounds.contains(clickPoint)) {
                draggedRoom = room;
                dragStart = new Point(clickPoint.x - room.bounds.x, clickPoint.y - room.bounds.y);
                originalBounds = new Rectangle(room.bounds);
                break;
            } }}
        private void handleMouseDragged(MouseEvent e) {
            if (draggedRoom != null) {
                int newX = e.getX() - dragStart.x;
                int newY = e.getY() - dragStart.y;
                newX = Math.round(newX / (float) GRID_SIZE) * GRID_SIZE;
                newY = Math.round(newY / (float) GRID_SIZE) * GRID_SIZE;
                newX = Math.max(0, Math.min(newX, getWidth() - draggedRoom.bounds.width));
                newY = Math.max(0, Math.min(newY, getHeight() - draggedRoom.bounds.height));
                int dx = newX - draggedRoom.bounds.x;
                int dy = newY - draggedRoom.bounds.y;
                draggedRoom.bounds.setLocation(newX, newY);
                for (Furniture furniture : draggedRoom.getFurniture()) {
                    Point currentPos = furniture.getBounds().getLocation();
                    furniture.setPosition(new Point(currentPos.x + dx, currentPos.y + dy));
                }
                draggedRoom.updateFixturePositions(dx, dy);
                repaint();
            } else if (draggedFurniture != null) {
                int newX = e.getX() - draggedFurnitureStart.x;
                int newY = e.getY() - draggedFurnitureStart.y;
                // Snap to grid
                newX = Math.round(newX / (float) GRID_SIZE) * GRID_SIZE;
                newY = Math.round(newY / (float) GRID_SIZE) * GRID_SIZE;
                // Ensure furniture stays within its parent room
                Room parentRoom = draggedFurniture.getParentRoom();
                if (parentRoom != null) {
                    Rectangle roomBounds = parentRoom.bounds;
                    newX = Math.max(roomBounds.x, Math.min(newX, 
                        roomBounds.x + roomBounds.width - draggedFurniture.getBounds().width));
                    newY = Math.max(roomBounds.y, Math.min(newY, 
                        roomBounds.y + roomBounds.height - draggedFurniture.getBounds().height));
                }
    
                draggedFurniture.setPosition(new Point(newX, newY));
                repaint();
            } else if (draggedFixture != null) {
                // Existing fixture dragging logic
                int newX = e.getX() - draggedFixtureStart.x;
                int newY = e.getY() - draggedFixtureStart.y;
    
                newX = Math.round(newX / (float) GRID_SIZE) * GRID_SIZE;
                newY = Math.round(newY / (float) GRID_SIZE) * GRID_SIZE;
    
                draggedFixture.setPosition(new Point(newX, newY));
                repaint();
            } }
        private void handleMouseReleased() {
            if (draggedFixture != null) {
                Room parentRoom = draggedFixture.getParentRoom();
                if (!parentRoom.bounds.contains(draggedFixture.getBounds())) {
                    draggedFixture.setPosition(originalFixturePosition);
                } else {
                    boolean hasOverlap = false;
                    for (Fixture other : parentRoom.getFixtures()) {
                        if (other != draggedFixture && draggedFixture.intersects(other)) {
                            hasOverlap = true;
                            break;
                        }
                    }
                    if (hasOverlap) {
                        draggedFixture.setPosition(originalFixturePosition);
                        JOptionPane.showMessageDialog(this,
                            "Cannot place fixture here - overlap detected!",
                            "Overlap Error",
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
                draggedFixture = null;
                draggedFixtureStart = null;
                originalFixturePosition = null;
                repaint();
            }
            if (draggedRoom != null) {
                boolean hasOverlap = false;
                Room overlappingRoom = null;
                if (!isWithinCanvas(draggedRoom.bounds)) {
                    draggedRoom.bounds.setRect(originalBounds);
                    JOptionPane.showMessageDialog(this,
                            "Cannot place room outside the canvas boundaries!",
                            "Boundary Error",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    for (Room room : rooms) {
                        if (room != draggedRoom && room.bounds.intersects(draggedRoom.bounds)) {
                            hasOverlap = true;
                            overlappingRoom = room;
                            break;
                        }
                    }
                    if (hasOverlap) {
                        String message = String.format("Cannot place %s here - it overlaps with %s!",
                                draggedRoom.type.replace("Add ", ""),
                                overlappingRoom.type.replace("Add ", ""));
                        JOptionPane.showMessageDialog(this,
                                message,
                                "Overlap Error",
                                JOptionPane.ERROR_MESSAGE);

                        draggedRoom.bounds.setRect(originalBounds);
                    }
                }
                selectedRoom = draggedRoom;
                updatePropertiesPanel(selectedRoom);
                draggedRoom = null;
                dragStart = null;
                originalBounds = null;
                repaint();
            }
        }
        private void handleRoomSelection(int x, int y) {
            selectedRoom = null;
            for (Room room : rooms) {
                if (room.bounds.contains(x, y)) {
                    selectedRoom = room;
                    break;
                }
            }
            updatePropertiesPanel(selectedRoom);
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            drawGrid(g);
        for (Room room : rooms) {
            drawRooms(g);
            if (room == selectedRoom) {
                g.setColor(Color.BLUE);
                g.drawRect(room.bounds.x - 2, room.bounds.y - 2,
                          room.bounds.width + 4, room.bounds.height + 4);
            }
        }// Draw selection rectangle for selected furniture
        if (selectedFurniture != null) {
            g.setColor(Color.GREEN);
            Rectangle bounds = selectedFurniture.getBounds();
            g.drawRect(bounds.x - 2, bounds.y - 2,
                      bounds.width + 4, bounds.height + 4);
        }// Draw selection rectangle for selected fixture
        if (selectedFixture != null) {
            g.setColor(Color.RED);
            Rectangle bounds = selectedFixture.getBounds();
            g.drawRect(bounds.x - 2, bounds.y - 2,
                      bounds.width + 4, bounds.height + 4);
        }
        }
        private void drawGrid(Graphics g) {
            g.setColor(GRID_COLOR);
            for (int x = 0; x < getWidth(); x += GRID_SIZE) {
                g.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += GRID_SIZE) {
                g.drawLine(0, y, getWidth(), y);
            }
        }
        private void drawRooms(Graphics g) {
            for (Room room : rooms) {
                if (room != draggedRoom) {
                    room.draw(g, room == selectedRoom);
                }
            }
            if (draggedRoom != null) {
                draggedRoom.draw(g, true);
            }
        }
    }
    private class Room implements Serializable {
        private static final long serialVersionUID = 1L;
        private Rectangle bounds;
        private Color color;
        private String type;
        private ArrayList<Fixture> fixtures;
        private ArrayList<Furniture> furniture;
        public Room(int x, int y, int width, int height, Color color, String type) {
            this.bounds = new Rectangle(x, y, width, height);
            this.color = color;
            this.type = type;
            this.fixtures = new ArrayList<>();
            this.furniture = new ArrayList<>();
        }
        public void addFurniture(Furniture furniture) {
            this.furniture.add(furniture);
        }public void removeFurniture(Furniture furniture) {
            this.furniture.remove(furniture);
        }public ArrayList<Furniture> getFurniture() {
            return furniture;
        }public void updateFurniturePositions(int dx, int dy) {
            for (Furniture furniture : furniture) {
                furniture.updatePosition(dx, dy);
            }
        }public void addFixture(Fixture fixture) {
            fixtures.add(fixture);
        }public void removeFixture(Fixture fixture) {
            fixtures.remove(fixture);
        }public ArrayList<Fixture> getFixtures() {
            return fixtures;
        }public void updateFixturePositions(int dx, int dy) {
            for (Fixture fixture : fixtures) {
                fixture.updatePosition(dx, dy);
            }
        }public void draw(Graphics g, boolean isSelected) {
            if (this == draggedRoom) {
                Color transparentColor = new Color(
                        color.getRed(),
                        color.getGreen(),
                        color.getBlue(),
                        180);
                g.setColor(transparentColor);
            } else {
                g.setColor(color);
            }
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
            if (isSelected) {
                g.setColor(Color.RED);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setStroke(new BasicStroke(2));
            } else {
                g.setColor(Color.BLACK);
            }
            g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
            g.setColor(Color.BLACK);
            FontMetrics fm = g.getFontMetrics();
            String label = type.replace("Add ", "");
            int textX = bounds.x + (bounds.width - fm.stringWidth(label)) / 2;
            int textY = bounds.y + (bounds.height + fm.getAscent()) / 2;
            g.drawString(label, textX, textY);
            // Draw fixtures
            for (Fixture fixture : fixtures) {
                fixture.draw(g);
                // Highlight selected fixture
                if (fixture == selectedFixture) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.BLUE);
                    g2d.setStroke(new BasicStroke(2));
                    Rectangle bounds = fixture.getBounds();
                    g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
            }
            for (Furniture furniture : furniture) {
                furniture.draw(g);
                // Highlight selected furniture
                if (furniture == selectedFurniture) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setColor(Color.GREEN);
                    g2d.setStroke(new BasicStroke(2));
                    Rectangle bounds = furniture.getBounds();
                    g2d.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }
            }
        }
        
    }   public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new FloorPlanner();
        });
    }
}