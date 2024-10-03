package template;

import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.simulation.Vehicle;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ReactiveTemplate_QValue implements ReactiveBehavior {

    private Random random;
    private double discountFactor;
    private double learningRate;
    private int numActions;
    private Agent myAgent;

    private Map<StateActionPair, Double> qValues;   // Q-values for state-action pairs
    private Map<City, List<City>> neighborsMap;     // Neighboring cities map

    private static final double EPSILON = 0.01;      // Exploration factor for epsilon-greedy action selection
    private static final int MAX_ITER = 10000;      // Max iterations for Q-value learning

    // State-action pair class to represent Q-values
    private class StateActionPair {
        City city;
        ActionType actionType; // MOVE or PICKUP

        public StateActionPair(City city, ActionType actionType) {
            this.city = city;
            this.actionType = actionType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StateActionPair pair = (StateActionPair) o;
            return actionType == pair.actionType && city.equals(pair.city);
        }

        @Override
        public int hashCode() {
            return city.hashCode() + (actionType == ActionType.PICKUP ? 1 : 0);
        }
    }

    // Action type to differentiate between MOVE and PICKUP actions
    private enum ActionType {
        MOVE, PICKUP
    }

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {

        // Initialize Q-values, neighbors map
        qValues = new HashMap<>();
        neighborsMap = new HashMap<>();

        // Populate the neighboring cities map from the topology
        for (City city : topology) {
            neighborsMap.put(city, city.neighbors());  // Store neighbors for each city
        }

        // Reads the discount factor and learning rate from the agents.xml file
        this.discountFactor = agent.readProperty("discount-factor", Double.class, 0.95);
        this.learningRate = agent.readProperty("learning-rate", Double.class, 0.1);

        this.random = new Random();
        this.numActions = 0;
        this.myAgent = agent;

        // Initialize Q-values for all state-action pairs
        for (City city : topology) {
            for (City neighbor : neighborsMap.get(city)) {
                qValues.put(new StateActionPair(city, ActionType.MOVE), 0.0);
            }
            qValues.put(new StateActionPair(city, ActionType.PICKUP), 0.0);
        }

        // Learn Q-values using Q-value iteration
        qValueIteration(topology, td);
    }

    /**
     * Q-value iteration to learn the Q-values for state-action pairs.
     */
    private void qValueIteration(Topology topology, TaskDistribution td) {
        for (int i = 0; i < MAX_ITER; i++) {
            // For each iteration, update the Q-values for each state-action pair
            for (City city : topology) {
                // Update Q-values for moving to neighboring cities
                for (City neighbor : neighborsMap.get(city)) {
                    updateQValue(city, neighbor, td, ActionType.MOVE);
                }

                // Update Q-values for picking up a task
                updateQValue(city, null, td, ActionType.PICKUP);
            }
        }
    }

    /**
     * Updates the Q-value for a specific state-action pair (either MOVE or PICKUP).
     */
    private void updateQValue(City city, City neighbor, TaskDistribution td, ActionType actionType) {
        double reward = 0.0;
        double futureValue = 0.0;

        if (actionType == ActionType.MOVE) {
            // Reward for moving to a neighboring city (no immediate reward, only future value)
            reward = 0.0;
            futureValue = getMaxQValue(neighbor);
        } else if (actionType == ActionType.PICKUP) {
            // Reward for picking up a task and delivering it
            for (City destination : neighborsMap.keySet()) {
                double taskReward = td.reward(city, destination);
                futureValue = Math.max(futureValue, taskReward + discountFactor * getMaxQValue(destination));
            }
        }

        // Update the Q-value using the Bellman update rule
        StateActionPair pair = new StateActionPair(city, actionType);
        double oldQValue = qValues.get(pair);
        double newQValue = (1 - learningRate) * oldQValue + learningRate * (reward + discountFactor * futureValue);
        qValues.put(pair, newQValue);
    }

    /**
     * Returns the maximum Q-value for any action in a given city (either moving or picking up).
     */
    private double getMaxQValue(City city) {
        double maxQValue = Double.NEGATIVE_INFINITY;

        for (City neighbor : neighborsMap.get(city)) {
            maxQValue = Math.max(maxQValue, qValues.getOrDefault(new StateActionPair(city, ActionType.MOVE), 0.0));
        }
        maxQValue = Math.max(maxQValue, qValues.getOrDefault(new StateActionPair(city, ActionType.PICKUP), 0.0));

        return maxQValue;
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        City currentCity = vehicle.getCurrentCity();
        Action action;

        // Epsilon-greedy action selection
        if (random.nextDouble() < EPSILON) {
            // Explore: choose a random action
            if (availableTask == null || random.nextBoolean()) {
                // Move to a random neighbor
                City nextCity = currentCity.randomNeighbor(random);
                action = new Move(nextCity);
            } else {
                // Pick up the task
                action = new Pickup(availableTask);
            }
        } else {
            // Exploit: choose the action with the highest Q-value
            double maxQValue = Double.NEGATIVE_INFINITY;
            City bestNeighbor = null;

            // Evaluate moving to neighbors
            for (City neighbor : neighborsMap.get(currentCity)) {
                double qValue = qValues.getOrDefault(new StateActionPair(currentCity, ActionType.MOVE), 0.0);
                if (qValue > maxQValue) {
                    maxQValue = qValue;
                    bestNeighbor = neighbor;
                }
            }

            // Default action is to move to the best neighbor
            action = new Move(bestNeighbor);

            // Evaluate picking up the task
            if (availableTask != null) {
                double pickUpQValue = qValues.getOrDefault(new StateActionPair(currentCity, ActionType.PICKUP), 0.0) + availableTask.reward;
                if (pickUpQValue > maxQValue) {
                    action = new Pickup(availableTask);
                }
            }
        }

        // Track the number of actions and print the profit information
        if (numActions >= 1) {
            System.out.println("The total profit after " + numActions + " actions is " +
                    myAgent.getTotalProfit() + " (average profit: " +
                    (myAgent.getTotalProfit() / (double) numActions) + ")");
        }
        numActions++;

        return action;
    }
}
