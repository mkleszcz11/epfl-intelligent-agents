import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;

import logist.simulation.Vehicle;
import logist.agent.Agent;
import logist.behavior.ReactiveBehavior;
import logist.plan.Action;
import logist.plan.Action.Move;
import logist.plan.Action.Pickup;
import logist.task.Task;
import logist.task.TaskDistribution;
import logist.topology.Topology;
import logist.topology.Topology.City;

public class ReactiveAgent implements ReactiveBehavior {

    private Random random;
    private double pPickup;
    private int numActions;
    private Agent myAgent;
    private ArrayList<State> stateSpace;
    private TransitionMatrix transitionMatrix;
    private RewardMatrix rewardMatrix;
    private HashMap<State, ActionClass> bestAction;

    @Override
    public void setup(Topology topology, TaskDistribution td, Agent agent) {

        // Reads the discount factor from the agents.xml file.
        // If the property is not present it defaults to 0.95
        Double discount = agent.readProperty("discount-factor", Double.class,
                0.95);

        this.random = new Random();
        this.pPickup = discount;
        this.numActions = 0;
        this.myAgent = agent;

        this.stateSpace = generateStateSpace(topology);
        generatePossibleActions(this.stateSpace);
        this.transitionMatrix = new TransitionMatrix(this.stateSpace, td, topology);
        this.transitionMatrix.generateTransitionMatrix();
        this.rewardMatrix = new RewardMatrix(this.stateSpace, td, this.myAgent);

        this.bestAction = learningProcessCity(0.0001, topology, td);
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        City currentCity = vehicle.getCurrentCity();
        State currentState;
        if (availableTask != null) {
            currentState = new State(currentCity, availableTask.deliveryCity);
        }
        else {
            currentState = new State(currentCity, null);
        }
        Action action;
        ActionClass actionToTake;

        City maxKey = null;
        double maxValue = Double.MIN_VALUE;

        actionToTake = this.bestAction.get(currentState);
        maxKey = actionToTake.getMove();

        if (actionToTake.isDelivery()) {
            action = new Pickup(availableTask);

        } else {
            action = new Move(maxKey);
        }

        if (numActions >= 1) {
            System.out.println("The total profit after "+numActions+" actions is "+myAgent.getTotalProfit()+" (average profit: "+(myAgent.getTotalProfit() / (double)numActions)+")");
        }
        numActions++;

        return action;
    }

    private ArrayList<State> generateStateSpace(Topology topology) {
        ArrayList<State> stateSpace = new ArrayList<>();
        for (City city : topology) {
            for (City to : topology) {
                if (!city.equals(to)) {
                    stateSpace.add(new State(city, to));
                }
            }
            stateSpace.add(new State(city, null));
        }
        return stateSpace;
    }

    private void generatePossibleActions(ArrayList<State> stateSpace) {
        for (State state : stateSpace) {
            state.generatePossibleActions();
        }
    }

    /*
    private HashMap<State, Double> learningProcess(double stoppingE) {
        HashMap<State, Double> statesValues =  new HashMap<>();
        HashMap<State, Double> statesValuesOld = new HashMap<>();

        for (State state : this.stateSpace) {
            double value = this.random.nextDouble();
            statesValues.put(state, value);
            statesValuesOld.put(state, value + stoppingE + 1.0);
        }

        double delta;
        do {
            statesValuesOld.clear();
            statesValuesOld.putAll(statesValues);
            for (State state : stateSpace) {
                HashMap<ActionClass, Double> qTable = new HashMap<>();
                for (ActionClass action : state.getPossibleActions()) {
                    Double qValue = rewardMatrix.getReward(state, action) + this.pPickup * sumTransitionValues(action, statesValuesOld);
                    qTable.put(action, qValue);
                }
                statesValues.put(state, qTable.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(Double.NaN));
            }

            delta = statesValues.keySet().stream()
                .filter(statesValuesOld::containsKey)
                .mapToDouble(key -> Math.abs(statesValues.get(key) - statesValuesOld.get(key)))
                .max()
                .orElse(stoppingE + 1.0);
        } while (delta > stoppingE);

        for (State state : statesValues.keySet()) {
            System.out.println("\n\n-----------------------------------------------------------------------");
            if (state.getDestination() != null) {
                System.out.println("STATE: (city = "+state.getCity().name+", destination = "+state.getDestination()+")");
            }
            else {
                System.out.println("STATE: (city = "+state.getCity().name+", destination = null)");
            }
            System.out.println("VALUE = "+statesValues.get(state));
        }

        return statesValues;
    }
    */

    private HashMap<State, ActionClass> learningProcessCity(double stoppingE, Topology topology, TaskDistribution td) {
        HashMap<State, Double> statesValues =  new HashMap<>();
        HashMap<State, Double> statesValuesOld = new HashMap<>();
        HashMap<State, ActionClass> bestAction = new HashMap<>();

        for (State state : stateSpace) {
            double value = this.random.nextDouble();
            statesValues.put(state, value);
        }

        double delta;
        do {
            statesValuesOld.clear();
            statesValuesOld.putAll(statesValues);
            for (State state : stateSpace) {
                // for each state
                HashMap<ActionClass, Double> qTable = new HashMap<>();
                for (ActionClass action : state.getPossibleActions()) {
                    // for each possible action in the state
                    // compute the reward of the action
                    double reward = rewardMatrix.getReward(state, action);
                    // compute the sum of reaching the next state with the action
                    double sum = sumTransitionValues(action, statesValues);
                    Double qValue = reward + this.pPickup * sum;
                    // update the qValue for the state
                    qTable.put(action, qValue);
                }
                double maxValue = Double.MIN_VALUE;
                for (ActionClass action : qTable.keySet()) {
                    // get the best entry (action, value) in the qTable
                    if (qTable.get(action) > maxValue) {
                        maxValue = qTable.get(action);
                        // update the state value and the best action for the state
                        statesValues.put(state, maxValue);
                        bestAction.put(state, action);
                    }
                }
            }

            delta = statesValues.keySet().stream()
                    .filter(statesValuesOld::containsKey)
                    .mapToDouble(key -> Math.abs(statesValues.get(key) - statesValuesOld.get(key)))
                    .max()
                    .orElse(stoppingE + 1.0);
        } while (delta > stoppingE);

        return bestAction;
    }

    private double sumTransitionValues(ActionClass action, HashMap<State, Double> statesValues) {
        double value = 0.0;
        HashMap<State, Double> probabilityDistribution = transitionMatrix.getTransition(action);
        for (State state1 : probabilityDistribution.keySet()) {
            value += probabilityDistribution.get(state1) * statesValues.get(state1);
        }
        return value;
    }
}
