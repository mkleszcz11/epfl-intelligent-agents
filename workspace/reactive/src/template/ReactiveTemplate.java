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

public class ReactiveTemplate implements ReactiveBehavior {

	private Random random;
	private double discountFactor;
	private int numActions;
	private Agent myAgent;

	private Map<City, Map<City, Double>> rewardMap; // Expected reward between cities
	private Map<City, List<City>> neighborsMap;     // Neighboring cities map
	private Map<State, Double> stateValues;         // Value of each state

	private static final double EPSILON = 0.01;     // Convergence threshold for Bellman update

	// State class to represent whether we are in a city with or without a package
	private class State {
		City city;
		boolean hasPackage; // true if the agent has a package, false otherwise

		public State(City city, boolean hasPackage) {
			this.city = city;
			this.hasPackage = hasPackage;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			State state = (State) o;
			return hasPackage == state.hasPackage && city.equals(state.city);
		}

		@Override
		public int hashCode() {
			return city.hashCode() + (hasPackage ? 1 : 0);
		}
	}

	@Override
	public void setup(Topology topology, TaskDistribution td, Agent agent) {
		// Initialize reward map, neighbors map, and state values
		rewardMap = new HashMap<>();
		neighborsMap = new HashMap<>();
		stateValues = new HashMap<>();

		// Populate the neighboring cities map from the topology
		for (City city : topology) {
			neighborsMap.put(city, city.neighbors());  // Store neighbors for each city
		}

		// Populate the expected reward between each pair of cities using TaskDistribution
		for (City from : topology) {
			Map<City, Double> cityRewardMap = new HashMap<>();
			for (City to : topology) {
				cityRewardMap.put(to, (double) td.reward(from, to)); // Get expected reward
			}
			rewardMap.put(from, cityRewardMap);
		}

		// Reads the discount factor from the agents.xml file. Defaults to 0.95 if not specified
		this.discountFactor = agent.readProperty("discount-factor", Double.class, 0.95);

		this.random = new Random();
		this.numActions = 0;
		this.myAgent = agent;

		// Initialize state values to 0
		for (City city : topology) {
			stateValues.put(new State(city, false), 0.0);
			stateValues.put(new State(city, true), 0.0);
		}

		// Perform value iteration to compute state values
		valueIteration(topology, td);
	}

	private void valueIteration(Topology topology, TaskDistribution td) {
		boolean hasConverged;
		do {
			hasConverged = true;

			// Iterate over all cities and states
			for (City city : topology) {
				State withoutPackage = new State(city, false);
				State withPackage = new State(city, true);

				// Update value for state without a package
				double oldValueWithoutPackage = stateValues.get(withoutPackage);
				double newValueWithoutPackage = 0.0;

				// Consider all possible neighbor moves
				for (City neighbor : neighborsMap.get(city)) {
					State neighborState = new State(neighbor, false);
					double neighborValue = stateValues.getOrDefault(neighborState, 0.0);
					newValueWithoutPackage = Math.max(newValueWithoutPackage, discountFactor * neighborValue);
				}

				// Consider picking up a task and transitioning to "with package" state
				for (City destination : rewardMap.get(city).keySet()) {
					double reward = td.reward(city, destination);
					State withPackageState = new State(destination, true);
					double withPackageValue = reward + discountFactor * stateValues.getOrDefault(withPackageState, 0.0);
					newValueWithoutPackage = Math.max(newValueWithoutPackage, withPackageValue);
				}

				stateValues.put(withoutPackage, newValueWithoutPackage);
				if (Math.abs(newValueWithoutPackage - oldValueWithoutPackage) > EPSILON) {
					hasConverged = false;
				}

				// Update value for state with a package (deliver to destination)
				double oldValueWithPackage = stateValues.get(withPackage);
				double newValueWithPackage = 0.0;

				for (City neighbor : neighborsMap.get(city)) {
					State neighborState = new State(neighbor, false);
					double neighborValue = stateValues.getOrDefault(neighborState, 0.0);
					newValueWithPackage = Math.max(newValueWithPackage, discountFactor * neighborValue);
				}

				stateValues.put(withPackage, newValueWithPackage);
				if (Math.abs(newValueWithPackage - oldValueWithPackage) > EPSILON) {
					hasConverged = false;
				}
			}
		} while (!hasConverged);
	}

	@Override
	public Action act(Vehicle vehicle, Task availableTask) {
		City currentCity = vehicle.getCurrentCity();
		State currentState = new State(currentCity, false); // Initial state is without a package
		Action action;

		if (availableTask == null) {
			// No task available, choose to move to the neighbor with the highest future value
			double maxFutureValue = Double.NEGATIVE_INFINITY;
			City bestNeighbor = null;

			for (City neighbor : neighborsMap.get(currentCity)) {
				State nextState = new State(neighbor, false); // Next state will also be without a package
				double futureValue = stateValues.getOrDefault(nextState, 0.0);
				if (futureValue > maxFutureValue) {
					maxFutureValue = futureValue;
					bestNeighbor = neighbor;
				}
			}

			// Move to the neighbor city with the highest future state value
			action = new Move(bestNeighbor);

		} else {
			// Task is available, compare moving vs. picking up the task
			// 1. Value of picking up the task and delivering it
			State withPackageState = new State(availableTask.deliveryCity, true);
			double pickUpValue = stateValues.getOrDefault(withPackageState, 0.0) + availableTask.reward;

			// 2. Value of moving to a neighboring city without picking up the task
			double maxMoveValue = Double.NEGATIVE_INFINITY;
			City bestNeighbor = null;

			for (City neighbor : neighborsMap.get(currentCity)) {
				State nextState = new State(neighbor, false); // Next state without a package
				double futureValue = stateValues.getOrDefault(nextState, 0.0);
				if (futureValue > maxMoveValue) {
					maxMoveValue = futureValue;
					bestNeighbor = neighbor;
				}
			}

			// Compare the two values and decide whether to pick up the task or move
			if (pickUpValue > maxMoveValue) {
				action = new Pickup(availableTask); // Pick up the task
			} else {
				action = new Move(bestNeighbor); // Move to the best neighboring city
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
