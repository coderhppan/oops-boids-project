package com.trial;

import java.util.ArrayList;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;


public class Main extends Application {

    /**
     * Builds the simulation scene and starts the animation loop.
     *
     * The setup work here includes creating the root pane, spawning the initial
     * flock, adding interaction handlers, and configuring a debug overlay that
     * can highlight selected boids and their nearby neighbours.
     */
    @Override
    public void start(Stage stage) {
        // The root pane contains all visible elements: background, boids,
        // obstacles, and the floating control bar.
        Pane root = new Pane();
        Scene scene = new Scene(root, 960, 640);

        root.setStyle("-fx-background-color: #101820;");

        // Reset the global boid registry before creating a new flock. This prevents
        // stale birds from being considered part of the current simulation.
        Birb.clearFlock();

        // Use a fixed seed so the initial random layout is reproducible every run.
        Random random = new Random(12345L);

        // Obstacles are stored separately and can be added or removed while the
        // simulation is running.
        ArrayList<Obstacle> obstacles = new ArrayList<>();

        // Number of boids in the flock and the minimum spacing used when choosing
        // a random spawn position.
        int flockSize = 70;
        Birb[] birds = new Birb[flockSize];
        double minimumSpawnDistance = 2.0 * SimulationConfig.BODY_LENGTH;

        // Create each bird in a non-overlapping position, then attach its JavaFX
        // shape to the scene graph so it can be rendered. We use the reset
        // overload here too so the initial placement and later reset behavior share
        // the same randomisation rules.
        for (int i = 0; i < flockSize; i++) {
            birds[i] = new Birb(0, 0, new Vector(1, 0));
            root.getChildren().add(birds[i].getNode());
            birds[i].reset(random, scene.getWidth(), scene.getHeight(),
                    minimumSpawnDistance, birds, i, obstacles);
        }

        // A debug overlay is layered on top of the flock so selected boids can
        // draw neighbour lines without affecting the main simulation visuals.
        Pane debugOverlay = new Pane();
        debugOverlay.setMouseTransparent(true);
        root.getChildren().add(debugOverlay);

        // Obstacles are visualised in the same scene group as the boids.
        for (Obstacle obstacle : obstacles) {
            root.getChildren().add(obstacle.getNode());
        }

        // Help text explains keyboard and mouse controls to the user.
        Label help = new Label("R: reset flock   Click a boid: show/hide neighbours");
        help.setTextFill(Color.rgb(220, 230, 235, 0.8));

        // Each checkbox toggles one boids behaviour rule, letting the user see
        // how separation, alignment, and cohesion affect the flock in real time.
        CheckBox separationToggle = createToggle("Separation", false);
        CheckBox velocityMatchingToggle = createToggle(
                "Velocity matching / alignment", false);
        CheckBox flockCenteringToggle = createToggle(
                "Flock centering / cohesion", false);
        CheckBox addObstacleToggle = createToggle("Add obstacles", false);

        // Paused state is stored in a mutable array because JavaFX's event handlers
        // need a final or effectively-final reference to a value that can change.
        final boolean[] paused = {false};

        // The pause/resume button flips the simulation state and changes its label.
        Button pauseButton = new Button("Pause");
        pauseButton.setOnAction(event -> {
            paused[0] = !paused[0];
            pauseButton.setText(paused[0] ? "Resume" : "Pause");
        });

        // All controls are packed into a single horizontal bar that sits above the
        // simulation area.
        HBox controls = new HBox(12, help, separationToggle,
                velocityMatchingToggle, flockCenteringToggle, addObstacleToggle,
                pauseButton);
        controls.setTranslateX(14);
        controls.setTranslateY(12);
        root.getChildren().add(controls);

        // Stores the currently selected boid for debug neighbour highlighting.
        final Birb[] selectedBird = {null};
        for (Birb bird : birds) {
            bird.getNode().setOnMouseClicked(event -> {
                // Toggle selection; clicking the same bird again clears the selection.
                selectedBird[0] = selectedBird[0] == bird ? null : bird;
                event.consume();
            });
        }

        // Configure and display the JavaFX stage.
        stage.setTitle("Boids simulation");
        stage.setScene(scene);
        stage.show();
        root.requestFocus();

        // Pressing R resets all boids to a new random configuration and removes any
        // user-added obstacles that were not present during the initial state.
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case R -> {
                    for (Birb bird : birds) {
                        bird.reset(random, scene.getWidth(), scene.getHeight());
                    }
                    while (!obstacles.isEmpty()) {
                        Obstacle obstacle = obstacles.remove(obstacles.size() - 1);
                        root.getChildren().remove(obstacle.getNode());
                    }
                }
                default -> {
                }
            }
        });

        // Clicking the empty background while the obstacle-toggle is enabled adds a
        // new obstacle in the mouse position.
        scene.setOnMouseClicked(event -> {
            if (addObstacleToggle.isSelected() && event.getTarget() == root) {
                Obstacle obstacle = new Obstacle(event.getX(), event.getY(), 20);
                obstacles.add(obstacle);
                root.getChildren().add(obstacle.getNode());
            }
        });

        // Animation loop updates each boid's acceleration and position at a steady
        // frame rate. The time step is capped to prevent large jumps when the
        // system briefly stalls or the frame rate drops.
        AnimationTimer timer = new AnimationTimer() {
            private long lastFrame;

            @Override
            public void handle(long now) {
                if (lastFrame == 0) {
                    lastFrame = now;
                    return;
                }
                double timeStep = Math.min((now - lastFrame) / 1_000_000_000.0, 0.05);
                lastFrame = now;

                // While paused, we still refresh the debug overlay so the selected
                // boid's neighbour connections remain visible without animating the flock.
                if (paused[0]) {
                    updateDebugOverlay(debugOverlay, selectedBird[0],
                            separationToggle.isSelected(),
                            scene.getWidth(), scene.getHeight());
                    return;
                }

                // First compute the acceleration for each boid based on local rules.
                Vector[] accelerations = new Vector[birds.length];
                for (int i = 0; i < birds.length; i++) {
                    accelerations[i] = birds[i].navModule(
                            obstacles, scene.getWidth(), scene.getHeight(),
                            separationToggle.isSelected(),
                            velocityMatchingToggle.isSelected(),
                            flockCenteringToggle.isSelected());
                }

                // Then apply those accelerations to update positions and headings.
                for (int i = 0; i < birds.length; i++) {
                    birds[i].flightModule(
                            accelerations[i], timeStep,
                            scene.getWidth(), scene.getHeight());
                }

                // Re-draw debug lines only after each update step.
                updateDebugOverlay(debugOverlay, selectedBird[0],
                        separationToggle.isSelected(),
                        scene.getWidth(), scene.getHeight());
            }
        };
        timer.start();
    }

    private CheckBox createToggle(String text, boolean selected) {
        CheckBox toggle = new CheckBox(text);
        toggle.setSelected(selected);
        toggle.setTextFill(Color.rgb(220, 230, 235, 0.9));
        return toggle;
    }

    /**
     * Draws debug connections for the selected boid.
     *
     * This overlay is used when a user clicks a boid to inspect how it relates to
     * nearby neighbours. When separation is enabled, the lines show the wraparound
     * neighbour links across the simulation boundaries.
     */
    private void updateDebugOverlay(Pane overlay, Birb bird,
                                    boolean separationEnabled,
                                    double width, double height) {
        // Clear previous debug lines before each refresh.
        overlay.getChildren().clear();
        if (bird == null) {
            return;
        }

        if (separationEnabled) {
            for (Vector neighbour : bird.neighbourPositions(width, height)) {
                addWrappedConnection(overlay, bird.position, neighbour,
                        width, height);
            }
        }

    }

    /**
     * Draws a line between two points, even when the end point crosses the scene
     * boundary and must be wrapped around the map edges.
     *
     * This is particularly useful for visualising neighbour relationships in a
     * toroidal world where boids may appear on the opposite side of the scene.
     */
    private void addWrappedConnection(Pane overlay, Vector start, Vector end,
                                      double width, double height) {
        double endX = end.x;
        double endY = end.y;
        if (endX >= 0 && endX < width && endY >= 0 && endY < height) {
            addConnection(overlay, start.x, start.y, endX, endY);
            return;
        }

        if (endX < 0 || endX >= width) {
            double edgeX = endX < 0 ? 0 : width;
            double wrappedX = endX < 0 ? endX + width : endX - width;
            double ratio = (edgeX - start.x) / (endX - start.x);
            double edgeY = start.y + (endY - start.y) * ratio;
            addConnection(overlay, start.x, start.y, edgeX, edgeY);
            addConnection(overlay, wrappedX, edgeY, wrappedX
                    + (endX - edgeX), endY);
            return;
        }

        if (endY < 0 || endY >= height) {
            double edgeY = endY < 0 ? 0 : height;
            double wrappedY = endY < 0 ? endY + height : endY - height;
            double ratio = (edgeY - start.y) / (endY - start.y);
            double edgeX = start.x + (endX - start.x) * ratio;
            addConnection(overlay, start.x, start.y, edgeX, edgeY);
            addConnection(overlay, edgeX, wrappedY, endX, wrappedY
                    + (endY - edgeY));
            return;
        }

        addConnection(overlay, start.x, start.y, endX, endY);
    }

    /**
     * Draws a single segment for the debug overlay.
     */
    private void addConnection(Pane overlay, double startX, double startY,
                               double endX, double endY) {
        Line connection = new Line(startX, startY, endX, endY);
        connection.setStroke(Color.rgb(255, 170, 80, 0.65));
        connection.setStrokeWidth(1.2);
        overlay.getChildren().add(connection);
    }
    public static void main(String[] args) {
        launch();
    }
}

/**
 * A collection of tuning constants used by the boids simulation.
 *
 * These values control flock behaviour, motion limits, and collision geometry.
 */
final class SimulationConfig {

    static final double BODY_LENGTH = 18.0;
    static final double BODY_RADIUS = 6.5;
    static final double MIN_VELOCITY = 36.0;
    static final double MAX_VELOCITY = 200.0;
    static final double[] AVOIDANCE_ANGLES = {
            0, 15, -15, 30, -30, 45, -45, 60, -60, 75, -75, 90, -90
    };
    static final double RAY_LENGTH = 90.0;
    static final double SAFETY_MARGIN = 0.0;
    static final double OBSTACLE_AVOIDANCE_WEIGHT = 10.0;gi
    static final double SEPARATION_WEIGHT = 2.3;
    static final double ALIGNMENT_WEIGHT = 1.0;
    static final double COHESION_WEIGHT = 0.5;
    static final double AVOIDANCE_RADIUS = BODY_LENGTH * 2.0;
    static final double COLLISION_RADIUS = BODY_RADIUS * 2.0;
    static final double COLLISION_PREDICTION_RADIUS = COLLISION_RADIUS + 10.0;
    static final double COLLISION_PREDICTION_TIME = 0.80;
    static final double PERCEPTION_RADIUS = BODY_LENGTH * 5.0;
    static final double MAX_STEER_FORCE = 80.0;

    private SimulationConfig() {
    }
}

class Obstacle {

    final Vector position;
    final double radius;
    private final Circle node;

    Obstacle(double x, double y, double radius) {
        this.position = new Vector(x, y);
        this.radius = radius;
        this.node = new Circle(this.position.x, this.position.y, this.radius);
        this.node.setFill(Color.DARKGRAY);
    }

    public Circle getNode(){
        return node;
    }
}

class Vector{
        double x;
        double y;

        Vector(double x, double y)
        {
            this.x = x;
            this.y = y;
        }

        Vector add(Vector other)
        {
            return new Vector(this.x + other.x, this.y + other.y);
        }

        Vector subtract(Vector other)
        {
            return new Vector(this.x - other.x, this.y - other.y);
        }

        Vector scale(double factor)
        {
            return new Vector(this.x * factor, this.y * factor);
        }

        double magnitude()
        {
            return Math.hypot(this.x, this.y);
        }

        Vector normalized()
        {
            double magnitude = magnitude();
            if(magnitude == 0)
            {
                return new Vector(0, 0);
            }

            return scale(1 / magnitude);
        }

        Vector limited(double maximumMagnitude)
        {
            double magnitude = magnitude();
            if(magnitude <= maximumMagnitude)
            {
                return new Vector(x, y);
            }

            return normalized().scale(maximumMagnitude);
        }
}

class Polar{
    double radius;
    double angle;

    Polar(double radius, double angle)
    {
        this.radius = radius;
        this.angle  = angle;
    }
}

class Birb{

    Polygon body;
    Vector position;
    Vector velocity;
    Vector heading;
    double bodylength;
    private static ArrayList<Birb> flock = new ArrayList<>();

    static void clearFlock(){
        flock.clear();
    }

    private class Behaviour{

        Vector collisionAvoidance;
        Vector velocityMatching;
        Vector flockCentering;
        Vector obstacleAvoidance;

        Behaviour(ArrayList<Obstacle> obstacles, double width, double height){
            this.collisionAvoidance = new Vector(0, 0);
            this.velocityMatching = new Vector(0, 0);
            this.flockCentering = new Vector(0, 0);
            this.obstacleAvoidance = calculateObstacleAvoidance(obstacles);
            calculateFlocking(neighbours(width, height), width, height);
        }

        private void calculateFlocking(ArrayList<Birb> neighbourlist,
                                       double width, double height)
        {
            if(!neighbourlist.isEmpty())
            {
                Vector averageHeading = new Vector(0, 0);
                Vector separationHeading = new Vector(0, 0);
                Vector centroid = new Vector(0, 0);
                for(Birb neighbour : neighbourlist)
                {
                    Vector localNeighbour = wrappedOffset(
                            neighbour.position, width, height);
                    Vector offsetToNeighbour = localNeighbour.subtract(position);
                    double squaredDistance = offsetToNeighbour.x * offsetToNeighbour.x
                            + offsetToNeighbour.y * offsetToNeighbour.y;
                    if (squaredDistance < SimulationConfig.AVOIDANCE_RADIUS
                            * SimulationConfig.AVOIDANCE_RADIUS) {
                        double distance = Math.sqrt(squaredDistance);
                        Vector staticAvoidance = distance > 0.000001
                                ? offsetToNeighbour.scale(-1.0 / distance)
                                : separationDirectionForOverlap(neighbour);
                        double distanceUrgency = (SimulationConfig.AVOIDANCE_RADIUS
                                - distance) / SimulationConfig.AVOIDANCE_RADIUS;
                        separationHeading = separationHeading.add(
                                staticAvoidance.scale(distanceUrgency
                                        * distanceUrgency * 0.5));

                        Vector relativeVelocity = neighbour.velocity.subtract(velocity);
                        double relativeSpeedSquared = relativeVelocity.x
                                * relativeVelocity.x
                                + relativeVelocity.y * relativeVelocity.y;
                        double timeToClosestApproach = relativeSpeedSquared > 0.000001
                                ? -(offsetToNeighbour.x * relativeVelocity.x
                                        + offsetToNeighbour.y * relativeVelocity.y)
                                        / relativeSpeedSquared
                                : 0;
                        timeToClosestApproach = Math.max(0, Math.min(
                                SimulationConfig.COLLISION_PREDICTION_TIME,
                                timeToClosestApproach));

                        Vector predictedOffset = offsetToNeighbour.add(
                                relativeVelocity.scale(timeToClosestApproach));
                        double predictedDistance = predictedOffset.magnitude();
                        if (predictedDistance < SimulationConfig.COLLISION_PREDICTION_RADIUS) {
                            Vector avoidanceDirection = predictedDistance > 0.000001
                                    ? predictedOffset.scale(-1.0 / predictedDistance)
                                    : separationDirectionForOverlap(neighbour);
                            double urgency = (SimulationConfig.COLLISION_PREDICTION_RADIUS
                                    - predictedDistance)
                                    / SimulationConfig.COLLISION_PREDICTION_RADIUS;
                            separationHeading = separationHeading.add(
                                    avoidanceDirection.scale(urgency * urgency));
                        }
                    }
                    averageHeading = averageHeading.add(neighbour.heading);
                    centroid = centroid.add(localNeighbour);
                }

                double neighbourCount = neighbourlist.size();
                velocityMatching = steerTowards(averageHeading.scale(1 / neighbourCount));
                flockCentering = steerTowards(
                        centroid.scale(1 / neighbourCount).subtract(position));
                collisionAvoidance = steerTowards(separationHeading);
            }
        }

        private Vector calculateObstacleAvoidance(ArrayList<Obstacle> obstacles)
        {
            if (nearestObstacleHit(position, heading, obstacles)
                    >= SimulationConfig.RAY_LENGTH) {
                return new Vector(0, 0);
            }
            Vector direction = firstClearDirection(position, heading, obstacles);
            return direction == null ? new Vector(0, 0) : steerTowards(direction);
        }

        private Vector steerTowards(Vector desiredDirection)
        {
            if(desiredDirection.magnitude() == 0)
            {
                return new Vector(0, 0);
            }
            Vector desiredVelocity = desiredDirection.normalized()
                    .scale(SimulationConfig.MAX_VELOCITY);
            return desiredVelocity.subtract(velocity).limited(
                    SimulationConfig.MAX_STEER_FORCE);
        }

        private Vector firstClearDirection(Vector origin, Vector forward,
                                           ArrayList<Obstacle> obstacles)
        {
            for(double angle : SimulationConfig.AVOIDANCE_ANGLES)
            {
                Vector direction = rotate(forward, Math.toRadians(angle));
                boolean obstacleHit = nearestObstacleHit(origin, direction, obstacles)
                        < SimulationConfig.RAY_LENGTH;
                if(!obstacleHit)
                {
                    return direction;
                }
            }
            return null;
        }

        private Vector rotate(Vector vector, double angle)
        {
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            return new Vector(
                    vector.x * cosine - vector.y * sine,
                    vector.x * sine + vector.y * cosine);
        }

        private double nearestObstacleHit(Vector origin, Vector direction,
                                          ArrayList<Obstacle> obstacles)
        {
            double nearest = SimulationConfig.RAY_LENGTH;
            for(Obstacle obstacle : obstacles)
            {
                double hit = rayCircleHit(origin, direction, obstacle);
                nearest = Math.min(nearest, hit);
            }
            return nearest;
        }

        private double rayCircleHit(Vector origin, Vector direction, Obstacle obstacle)
        {
            Vector toCenter = obstacle.position.subtract(origin);
            double radius = obstacle.radius + SimulationConfig.SAFETY_MARGIN
                    + SimulationConfig.BODY_RADIUS;
            if (toCenter.magnitude() <= radius) {
                return 0;
            }
            double projection = toCenter.x * direction.x + toCenter.y * direction.y;
            if(projection <= 0)
            {
                return SimulationConfig.RAY_LENGTH;
            }

            double perpendicularSquared = toCenter.magnitude() * toCenter.magnitude()
                    - projection * projection;
            double offsetSquared = radius * radius - perpendicularSquared;
            if(offsetSquared < 0)
            {
                return SimulationConfig.RAY_LENGTH;
            }

            double hit = projection - Math.sqrt(offsetSquared);
            return hit >= 0 && hit <= SimulationConfig.RAY_LENGTH
                    ? hit : SimulationConfig.RAY_LENGTH;
        }

        private Vector separationDirectionForOverlap(Birb neighbour)
        {
            Vector currentOffset = position.subtract(neighbour.position);
            if(currentOffset.magnitude() > 0.001)
            {
                return currentOffset.normalized();
            }

            int indexDifference = flock.indexOf(Birb.this) - flock.indexOf(neighbour);
            double angle = indexDifference * 2.399963;
            return new Vector(Math.cos(angle), Math.sin(angle));
        }

    }

    Birb(int x, int y){
        this(x, y, new Vector(60, 10));
    }

    Birb(double x, double y, Vector initialVelocity){
        this.position = new Vector(x, y);

        this.velocity = initialVelocity.normalized()
                .scale(Math.min(initialVelocity.magnitude(), SimulationConfig.MAX_VELOCITY));
        this.heading = this.velocity.normalized();
       
        this.bodylength = SimulationConfig.BODY_LENGTH;
        flock.add(this);

        this.body = new Polygon(
            13.0, 0.0,
            -SimulationConfig.BODY_RADIUS, SimulationConfig.BODY_RADIUS,
            -SimulationConfig.BODY_RADIUS, -SimulationConfig.BODY_RADIUS
            );
        
        body.setFill(Color.WHITE);
        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
    }

    void flightModule(Vector acceleration, double timeStep, double width, double height){
        velocity = velocity.add(acceleration.scale(timeStep));
        double speed = velocity.magnitude();
        if(speed > 0)
        {
            speed = Math.max(SimulationConfig.MIN_VELOCITY,
                    Math.min(SimulationConfig.MAX_VELOCITY, speed));
            velocity = velocity.normalized().scale(speed);
            heading = velocity.normalized();
        }
        else
        {
            velocity = heading.scale(SimulationConfig.MIN_VELOCITY);
        }
        position = position.add(velocity.scale(timeStep));

        wrapAround(width, height);

        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
        if(velocity.magnitude() > 0)
        {
            body.setRotate(Math.toDegrees(Math.atan2(velocity.y, velocity.x)));
        }
    }

    void wrapAround(double width, double height) {
        if (position.x < 0) {
            position.x += width;
        } else if (position.x >= width) {
            position.x -= width;
        }
        if (position.y < 0) {
            position.y += height;
        } else if (position.y >= height) {
            position.y -= height;
        }
        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
    }

    void reset(Random random, double width, double height) {
        position = new Vector(
                SimulationConfig.BODY_RADIUS
                        + random.nextDouble() * (width - 2 * SimulationConfig.BODY_RADIUS),
                SimulationConfig.BODY_RADIUS
                        + random.nextDouble() * (height - 2 * SimulationConfig.BODY_RADIUS));
        double bearing = random.nextDouble() * 2 * Math.PI;
        velocity = new Vector(Math.cos(bearing), Math.sin(bearing))
                .scale(SimulationConfig.MIN_VELOCITY);
        heading = velocity.normalized();
        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
    }

    void reset(Random random, double width, double height,
               double minimumDistance, Birb[] birds, int birdCount,
               ArrayList<Obstacle> obstacles) {
        for (int attempt = 0; attempt < 10000; attempt++) {
            Vector candidate = new Vector(
                    SimulationConfig.BODY_RADIUS + random.nextDouble() * (width - 2 * SimulationConfig.BODY_RADIUS),
                    SimulationConfig.BODY_RADIUS + random.nextDouble() * (height - 2 * SimulationConfig.BODY_RADIUS));

            boolean positionIsClear = true;
            for (int i = 0; i < birdCount; i++) {
                if (birds[i] != null && birds[i] != this
                        && candidate.subtract(birds[i].position).magnitude() < minimumDistance) {
                    positionIsClear = false;
                    break;
                }
            }

            for (Obstacle obstacle : obstacles) {
                double clearance = obstacle.radius + SimulationConfig.SAFETY_MARGIN
                        + SimulationConfig.BODY_RADIUS;
                if (candidate.subtract(obstacle.position).magnitude() < clearance) {
                    positionIsClear = false;
                    break;
                }
            }

            if (positionIsClear) {
                position = candidate;
                double bearing = random.nextDouble() * 2 * Math.PI;
                velocity = new Vector(Math.cos(bearing), Math.sin(bearing))
                        .scale(SimulationConfig.MIN_VELOCITY);
                heading = velocity.normalized();
                body.setTranslateX(position.x);
                body.setTranslateY(position.y);
                return;
            }
        }

        throw new IllegalStateException("Could not place a bird without overlapping others or obstacles");
    }

    Vector navModule(ArrayList<Obstacle> obstacles, double width, double height,
                     boolean separationEnabled, boolean velocityMatchingEnabled,
                     boolean flockCenteringEnabled){
        Behaviour behaviour = new Behaviour(obstacles, width, height);
        Vector acceleration = behaviour.obstacleAvoidance.scale(
                        SimulationConfig.OBSTACLE_AVOIDANCE_WEIGHT)
                .add(separationEnabled
                        ? behaviour.collisionAvoidance.scale(
                                SimulationConfig.SEPARATION_WEIGHT)
                        : new Vector(0, 0))
                .add(velocityMatchingEnabled
                        ? behaviour.velocityMatching.scale(
                                SimulationConfig.ALIGNMENT_WEIGHT)
                        : new Vector(0, 0))
                .add(flockCenteringEnabled
                        ? behaviour.flockCentering.scale(
                                SimulationConfig.COHESION_WEIGHT)
                        : new Vector(0, 0));
        return acceleration;
    }

    ArrayList<Birb> neighbours(double width, double height){

        ArrayList<Birb> neighbours = new ArrayList<>();

        for(int i = 0;i<flock.size();i++)
        {
            Birb curr = flock.get(i);
            if(curr == this)
            {
                continue;
            }
            Polar p = this.localize(wrappedOffset(curr.position, width, height));
            if (p.radius * bodylength > SimulationConfig.PERCEPTION_RADIUS)
            {
                continue;
            }

            neighbours.add(curr);
        }

        return neighbours;
    }

    ArrayList<Vector> neighbourPositions(double width, double height) {
        ArrayList<Vector> positions = new ArrayList<>();
        for (Birb neighbour : neighbours(width, height)) {
            positions.add(wrappedOffset(neighbour.position, width, height));
        }
        return positions;
    }


    public Polygon getNode(){
        return body;
    } 

    private Vector wrappedOffset(Vector target, double width, double height) {
        double dx = target.x - position.x;
        double dy = target.y - position.y;
        if (dx > width * 0.5) {
            dx -= width;
        } else if (dx < -width * 0.5) {
            dx += width;
        }
        if (dy > height * 0.5) {
            dy -= height;
        } else if (dy < -height * 0.5) {
            dy += height;
        }
        return new Vector(position.x + dx, position.y + dy);
    }

    public Polar localize(Vector pos){
        
        double dx = (pos.x-this.position.x)/this.bodylength;
        double dy = (pos.y-this.position.y)/this.bodylength;
        double diff = Math.atan2(dy, dx)-Math.atan2(this.velocity.y,this.velocity.x);

        Polar p = new Polar(Math.hypot(dx, dy),Math.atan2(Math.sin(diff),Math.cos(diff)));

        return p;
    }
}