/** ------------------------------------------------------------------------- *
 * libpomdp
 * ========
 * File: 
 * Description: Represent a POMDP model using a flat representation and
 *              sparse matrices and vectors. This class can be constructed
 *              from a pomdpSpecSparseMTJ object after parsing a .pomdp file.
 *              Sparse matrices by matrix-toolkits-java, 
 *              every matrix will be SparseMatrix:
 *              
 * S =
 *  (3,1)        1
 *  (2,2)        2
 *  (3,2)        3
 *  (4,3)        4
 *  (1,4)        5
 * A =
 *   0     0     0     5
 *   0     2     0     0
 *   1     3     0     0
 *   0     0     4     0
 * Copyright (c) 2009, 2010, 2011 Diego Maniloff
 * Copyright (c) 2010, 2011 Mauricio Araya
 --------------------------------------------------------------------------- */

package libsdm.pomdp.plain;

// imports
import java.io.Serializable;
import java.util.Random;

import libsdm.common.DenseVector;
import libsdm.common.SparseVector;
import libsdm.common.Utils;
import libsdm.mdp.RewardFunction;
import libsdm.mdp.RewardMatrix;
import libsdm.mdp.SparseMdp;
import libsdm.mdp.TransitionMatrix;
import libsdm.mdp.ValueFunction;
import libsdm.pomdp.AlphaVector;
import libsdm.pomdp.BeliefReward;
import libsdm.pomdp.BeliefState;
import libsdm.pomdp.BeliefValueFunction;
import libsdm.pomdp.ObservationMatrix;
import libsdm.pomdp.Pomdp;
import libsdm.pomdp.PomdpReward;

public class DensePomdp extends SparseMdp implements Pomdp, Serializable  {

    /**
     * Generated by Eclipse.
     */
    private static final long serialVersionUID = -5511401938934887929L;

    // starting belief
    protected SparseBeliefState initBelief;
    
    // private nrObs
    protected int nrObs;
    
	// observation model: a x s' x o
    protected ObservationMatrix O;

    // observation names
    protected String obsStr[];

	private BeliefReward Rho;
	
	protected PlainPomdpProjector pao;
    
    // ------------------------------------------------------------------------
    // methods
    // ------------------------------------------------------------------------

    // / constructor
    public DensePomdp(ObservationMatrix O,TransitionMatrix T,BeliefReward Rho,
	    int nrSta, int nrAct, int nrObs, double gamma, String staStr[],
	    String actStr[], String obsStr[], SparseVector init) {
    	super(T,null,nrSta,nrAct,gamma,staStr,actStr,-1);
    	// allocate space for the pomdp models
    	if (Rho instanceof PomdpReward){
    		R= (RewardMatrix) Rho.getMdpReward();
    	}
    	this.nrObs = nrObs;
        this.obsStr = obsStr;
    	// set initial belief state
    	this.initBelief = new SparseBeliefState(init, 0.0);
    	this.O = O;
    	this.Rho = Rho;
    	pao=new PlainPomdpProjector(this);
    } // constructor

    public DensePomdp(DensePomdp pomdp) {
    	this(pomdp.O,(TransitionMatrix) pomdp.getTransitionMatrix(),pomdp.Rho,pomdp.nrSta, pomdp.nrAct, pomdp.nrObs, pomdp.gamma, pomdp.staStr,
	    pomdp.actStr, pomdp.obsStr, pomdp.initBelief);
    }

    // / tao(b,a,o)
    
    public ValueFunction backup(ValueFunction old, boolean async,int i) {
    	if (!(Rho instanceof PomdpReward)){
    		Utils.error("POMDP backups can be used only with State-based Rewards");
    	}
		return super.backup(old, async, i);
	}


    public AlphaVector blindProjection(AlphaVector alpha, int a, int i) {
    	AlphaVector vec = alpha.project(T, a, i);
    	vec.scale(gamma);
		vec.add(Rho.getTangentAlpha(a, (BeliefState) SparseBeliefState.getUniform(states()),i));
		return (vec);
    }
    
    
    public void changeRewardFunction(RewardFunction Rho) {
    	if (!(Rho instanceof BeliefReward))
    		Utils.error("Rho must be a BeliefReward for POMDPs");
		this.Rho=(BeliefReward) Rho;
	}
	
	public BeliefState getInitialBeliefState() {
    	return initBelief.copy();
    }

	public String getObservationString(int o) {
    	if (obsStr ==null)
    		return null;
    	else
    		return obsStr[o];
    }

	public double getRewardMax() {
		return Rho.max();
	}

	public double getRewardMin() {
		return Rho.min();
	}

	public BeliefValueFunction getRewardValueFunction(int a,int i) {
    	return  Rho.getValueFunction(a,i);
    }

	public BeliefState nextBeliefState(BeliefState b, int a, int o,int i){
		return pao.projectBelief(b, a, o, i);
	}
	
	/*public BeliefState nextBeliefState(BeliefState b, int a, int o,int i) {
    	SparseBeliefState bel = (SparseBeliefState)b;
    	// long start = System.currentTimeMillis();
    	// System.out.println("made it to tao");
    	SparseAlphaVector bVec;
    	SparseBeliefState bPrime;
    	// compute T[a] * b
    	bVec = bel.project(T, a,i);
    	// System.out.println("Elapsed in tao - T[a] * b" +
    	// (System.currentTimeMillis() - start));

    	// element-wise product with O[a](:,o)
    	
    	bVec.elementMult(((ObservationMatrix) O).getRow(o,a));
    	// System.out.println("Elapsed in tao - O[a] .* b2" +
    	// (System.currentTimeMillis() - start));

    	// compute P(o|b,a) - norm1 is the sum of the absolute values
    	double poba = bVec.norm(1.0);
    	// make sure we can normalize
    	if (poba < 0.00001) {
    		// System.err.println("Zero prob observation - resetting to initBelief");
    		// this branch will have poba = 0.0
    		bPrime = initBelief;
    	} else {
    		// safe to normalize now
    		bVec.normalize();
    		bPrime=SparseBeliefState.transform(bVec);
    		bPrime.setPoba(poba);
    	}
    	// System.out.println("Elapsed in tao" + (System.currentTimeMillis() -
    	// start));
    	// return
    	return bPrime;
    }
    */


	public int observations() {
    	return nrObs;
    }

	// P(o|b,a) in vector form for all o's
    public SparseVector observationProbabilities(BeliefState b, int a,int i) {
    	return pao.observationProbabilities((SparseBeliefState) b,a,i);
    	/*
    	 SparseBeliefState bel = (SparseBeliefState)b;
    	 SparseAlphaVector Tb = bel.project(getTransitionMatrix(),a,i);
    	 SparseAlphaVector Poba = Tb.project(O,a);
    	 Poba.normalize();
    	 return Poba;
    	 */
    }

	public int sampleObservation(BeliefState b, int a,int i) {
    	SparseVector vect=observationProbabilities(b,a,i);
    	return(vect.sample());
    }

	public int sampleNextObservation(int nstate, int action, Random gen) {
		return(O.sampleNextObservation(nstate, action, gen));
	}

	@Override
	public boolean isRewardStationary() {
		return Rho.stationary();
	}
	
	@Override
	public BeliefReward getRewardFunction() {
		return Rho;
	}
	
    public double getRewardMaxMin() {
    	double max_val = Double.NEGATIVE_INFINITY;
    	for (int a = 0; a < actions(); a++) {
    	    double test_val = Rho.min(a);
    	    //System.out.println(test_val);
    	    if (test_val > max_val)
    		max_val = test_val;
    	}
    	return max_val;
        }

    public AlphaVector getEmptyAlpha() {
		return new DenseAlphaVector(nrSta);
	}

    public AlphaVector getEmptyAlpha(int a) {
		return (new DenseAlphaVector(nrSta, a));
	}

	public AlphaVector getHomogeneAlpha(double val) {
		return(DenseAlphaVector.transform(DenseVector.getHomogeneous(nrSta,val)));
	}


	
	public BeliefValueFunction getEmptyValueFunction() {
		return(new SparseBeliefValueFunction());
	}

	public BeliefValueFunction getLowerBound() {
		SparseBeliefValueFunction vf = null;
		vf=new SparseBeliefValueFunction();
		for (int a = 0; a < actions(); a++) {
			double factor = 1.0 / (1.0 - gamma());
			double val = getRewardMin();
			val *= factor;
		    vf.push(DenseAlphaVector.transform(DenseVector.getHomogeneous(states(), val), a));
		}
		return(vf);
	}

	public AlphaVector getRewardAlpha(int a, BeliefState bel, int time) {
		return Rho.getTangentAlpha(a, bel, time);
	}

	//public TransitionMatrix getTransitionFunction() {
	//	return this.getTransitionMatrix();
	//}

	//public ObservationMatrix getObservationFunction() {
	//	return O;
	//}
	
	public ObservationMatrix getObservationMatrix() {
		return O;
	}

	public boolean isBlocked(int a, int o) {
		return pao.isBlocked(a, o);
	}

	public AlphaVector projectAlpha(AlphaVector alpha, int a, int o, int time) {
		return pao.projectAlpha(alpha, a, o, time);
	}
	
	


} // SparseMomdp.java

