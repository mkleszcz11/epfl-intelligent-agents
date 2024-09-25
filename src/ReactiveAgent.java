import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

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
    private HashMap<City, Double> cityValues;

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

        this.cityValues = learningProcessCity(0.01, topology, td);
    }

    @Override
    public Action act(Vehicle vehicle, Task availableTask) {
        City currentCity = vehicle.getCurrentCity();
        State currentState = new State(currentCity, null);
        Action action;

        City maxKey = null;
        double maxValue = Double.MIN_VALUE;

        for (ActionClass act : currentState.getPossibleActions()) {
            double value = evaluateAction(act);
            if (value > maxValue) {
                maxValue = value;
                maxKey = act.getMove();
            }
        }

        if (availableTask != null && availableTask.deliveryCity.equals(maxKey)) {
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

    private HashMap<City, Double> learningProcessCity(double stoppingE, Topology topology, TaskDistribution td) {
        HashMap<City, Double> citiesValues =  new HashMap<>();
        HashMap<State, Double> statesValues =  new HashMap<>();
        HashMap<State, Double> statesValuesOld = new HashMap<>();

        for (State state : stateSpace) {
            double value = this.random.nextDouble();
            statesValues.put(state, value);
        }

        double delta;
        do {
            statesValuesOld.clear();
            statesValuesOld.putAll(statesValues);
            for (State state : stateSpace) {
                HashMap<ActionClass, Double> qTable = new HashMap<>();
                for (ActionClass action : state.getPossibleActions()) {
                    double reward = rewardMatrix.getReward(state, action);
                    double sum = sumTransitionValues(action, statesValuesOld);
                    Double qValue = reward + this.pPickup * sum;
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

        for (City city : topology.cities()) {
            Double cityValue = 0.0;
            for (State state : statesValues.keySet()) {
                if (state.getCity().equals(city)) {
                    cityValue += statesValues.get(state) * td.probability(state.getCity(), state.getDestination());
                }
            }
            citiesValues.put(city, cityValue);
        }

        return citiesValues;
    }

    private double sumTransitionValues(ActionClass action, HashMap<State, Double> statesValues) {
        double value = 0.0;
        HashMap<State, Double> probabilityDistribution = transitionMatrix.getTransition(action);
        for (State state1 : probabilityDistribution.keySet()) {
            value += probabilityDistribution.get(state1) * statesValues.get(state1);
        }

        return value;
    }

    private double evaluateAction(ActionClass action) {
        return this.cityValues.get(action.getMove());
    }
}
