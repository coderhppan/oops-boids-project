package com.trial;

import java.util.ArrayList;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.stage.Stage;


public class Main extends Application {

    @Override
    public void start(Stage stage) {
        
        Pane root = new Pane();
        Scene scene = new Scene(root, 400, 200);

        root.setStyle("-fx-background-color: black;");
        Birb.clearFlock();
        Random random = new Random(12345L);
        int flockSize = 40;
        Birb[] birds = new Birb[flockSize];
        double minimumSpawnDistance = 1.25 * Birb.BODY_LENGTH;
        for(int i = 0; i < flockSize; i++)
        {
            Vector position = randomSpawnPosition(random, birds, i, minimumSpawnDistance,
                    scene.getWidth(), scene.getHeight());
            double bearing = random.nextDouble() * 2 * Math.PI;
            double speed = 40 + random.nextDouble() * 20;
            Vector velocity = new Vector(Math.cos(bearing), Math.sin(bearing)).scale(speed);
            birds[i] = new Birb(position.x, position.y, velocity);
            root.getChildren().add(birds[i].getNode());
        }

        stage.setTitle("Boids simulation");
        stage.setScene(scene);
        stage.show();

    
    AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            Vector[] accelerations = new Vector[birds.length];
            for(int i = 0; i < birds.length; i++)
            {
                accelerations[i] = birds[i].navModule().limited(Birb.MAX_ACCELERATION);
            }
            for(int i = 0; i < birds.length; i++)
            {
                birds[i].flightModule(
                        accelerations[i],
                        1.0 / 60.0,
                        scene.getWidth(),
                        scene.getHeight());
            }
        }
    };

timer.start();
    }

    private Vector randomSpawnPosition(Random random, Birb[] birds, int birdCount,
                                       double minimumDistance, double width, double height) {
        for(int attempt = 0; attempt < 10000; attempt++)
        {
            Vector candidate = new Vector(
                    minimumDistance + random.nextDouble() * (width - 2 * minimumDistance),
                    minimumDistance + random.nextDouble() * (height - 2 * minimumDistance));
            boolean positionIsClear = true;

            for(int i = 0; i < birdCount; i++)
            {
                if(candidate.subtract(birds[i].position).magnitude() < minimumDistance)
                {
                    positionIsClear = false;
                    break;
                }
            }

            if(positionIsClear)
            {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not place the flock without overlapping birds");
    }

    public static void main(String[] args) {
        launch();
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

    private static final double NEIGHBOUR_RADIUS_IN_BODY_LENGTHS = 10.0;
    private static final double FIELD_OF_VIEW_HALF_ANGLE = Math.toRadians(135);
    static final double BODY_LENGTH = 18.0;
    private static final double COLLISION_AVOIDANCE_K = 25.0;
    private static final double FLOCK_CENTERING_GAIN = 0.8;
    private static final double MAX_VELOCITY = 100.0;
    static final double MAX_ACCELERATION = 60.0;
    private static final double LOOKAHEAD = 0.9; 

    Polygon body;

    Vector position;

    Vector velocity;

    double bodylength;

    private static ArrayList<Birb> flock = new ArrayList<>();

    static void clearFlock(){
        flock.clear();
    }

    private class Behaviour{

        Vector collisionAvoidance;
        Vector velocityMatching;
        Vector flockCentering;

        Behaviour(){
            this(neighbours());
        }

        Behaviour(ArrayList<Birb> neighbourlist){
            this.collisionAvoidance = new Vector(0, 0);
            this.velocityMatching = new Vector(0, 0);
            this.flockCentering = new Vector(0, 0);

            if(neighbourlist.isEmpty())
            {
                return;
            }

            Vector averageVelocity = new Vector(0, 0);
            Vector centroid = new Vector(0, 0);

            for(int i = 0; i < neighbourlist.size(); i++)
            {
                Birb neighbour = neighbourlist.get(i);
                Vector futureMe = position.add(
                            velocity.scale(LOOKAHEAD));

                Vector futureNeighbour =
                        neighbour.position.add(
                            neighbour.velocity.scale(LOOKAHEAD));

            
                Vector offsetFromNeighbour = futureMe.subtract(futureNeighbour);
                double distanceInBodyLengths = Math.max(
                        offsetFromNeighbour.magnitude() / bodylength,
                        0.1); //keeping it sufficiently far away from 0

                if(offsetFromNeighbour.magnitude() > 0)
                {
                    double avoidanceStrength = COLLISION_AVOIDANCE_K
                            / (distanceInBodyLengths * distanceInBodyLengths);
                    this.collisionAvoidance = this.collisionAvoidance.add(
                            offsetFromNeighbour.normalized().scale(avoidanceStrength));
                }

                averageVelocity = averageVelocity.add(neighbour.velocity);
                centroid = centroid.add(neighbour.position);
            }

            double neighbourCount = neighbourlist.size();
            averageVelocity = averageVelocity.scale(1 / neighbourCount);
            centroid = centroid.scale(1 / neighbourCount);

            this.velocityMatching = averageVelocity.subtract(velocity);
            this.flockCentering = centroid.subtract(position).scale(FLOCK_CENTERING_GAIN);
        }
    }

    Birb(int x, int y){
        this(x, y, new Vector(60, 10));
    }

    Birb(double x, double y, Vector initialVelocity){
        this.position = new Vector(x, y);

        this.velocity = initialVelocity.normalized()
                .scale(Math.min(initialVelocity.magnitude(), MAX_VELOCITY));
       
        this.bodylength = BODY_LENGTH;
        flock.add(this);

        this.body = new Polygon(
            13.0, 0.0,
            -6.5, 6.5,
            -6.5,-6.5
            );
        
        body.setFill(Color.WHITE);
        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
    }

    void flightModule(Vector acceleration, double timeStep, double width, double height){
        velocity = velocity.add(acceleration.scale(timeStep)).limited(MAX_VELOCITY);
        position = position.add(velocity.scale(timeStep));

        if(position.x > width)
        {
            position.x = 0;
        }
        else if(position.x < 0)
        {
            position.x = width;
        }

        if(position.y > height)
        {
            position.y = 0;
        }
        else if(position.y < 0)
        {
            position.y = height;
        }

        body.setTranslateX(position.x);
        body.setTranslateY(position.y);
        if(velocity.magnitude() > 0)
        {
            body.setRotate(Math.toDegrees(Math.atan2(velocity.y, velocity.x)));
        }
    }

    Vector navModule(){
        Behaviour behaviour = new Behaviour();
        Vector acceleration = behaviour.collisionAvoidance.scale(2.5)
                .add(behaviour.velocityMatching.scale(1.0))
                .add(behaviour.flockCentering.scale(0.8));

        double remainingAcceleration = MAX_ACCELERATION - acceleration.magnitude();
        double speed = velocity.magnitude();
        double speedDeficit = MAX_VELOCITY - speed;
        if(remainingAcceleration > 0 && speedDeficit > 0 && speed > 0)
        {
            Vector speedRecovery = velocity.normalized()
                    .scale(Math.min(remainingAcceleration, speedDeficit));
            acceleration = acceleration.add(speedRecovery);
        }

        return acceleration;
    }

    ArrayList<Birb> neighbours(){

        ArrayList<Birb> neighbours = new ArrayList<>();

        for(int i = 0;i<flock.size();i++)
        {
            Birb curr = flock.get(i);
            if(curr == this)
            {
                continue;
            }
            Polar p = this.localize(curr.position);
            if(p.radius > NEIGHBOUR_RADIUS_IN_BODY_LENGTHS
                    || p.angle > FIELD_OF_VIEW_HALF_ANGLE
                    || p.angle < -FIELD_OF_VIEW_HALF_ANGLE) // outside neighbour view
            {
                continue;
            }

            neighbours.add(curr);
        }

        return neighbours;
    }


    public Polygon getNode(){
        return body;
    } 

    public Polar localize(Vector pos){
        
        double dx = (pos.x-this.position.x)/this.bodylength;
        double dy = (pos.y-this.position.y)/this.bodylength;
        double diff = Math.atan2(dy, dx)-Math.atan2(this.velocity.y,this.velocity.x);

        Polar p = new Polar(Math.hypot(dx, dy),Math.atan2(Math.sin(diff),Math.cos(diff)));

        return p;
    }
}