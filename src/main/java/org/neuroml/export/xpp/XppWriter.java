package org.neuroml.export.xpp;

import java.io.File;
import java.util.ArrayList;

import org.lemsml.jlems.expression.ParseError;
import org.lemsml.jlems.run.ActionBlock;
import org.lemsml.jlems.run.ExpressionDerivedVariable;
import org.lemsml.jlems.run.PathDerivedVariable;
import org.lemsml.jlems.run.VariableAssignment;
import org.lemsml.jlems.run.VariableROC;
import org.lemsml.jlems.sim.LemsProcess;
import org.lemsml.jlems.sim.Sim;
import org.lemsml.jlems.type.Component;
import org.lemsml.jlems.type.Target;
import org.lemsml.jlems.type.Lems;
import org.lemsml.jlems.type.LemsCollection;
import org.lemsml.jlems.type.ParamValue;
import org.lemsml.jlems.type.Parameter;
import org.lemsml.jlems.type.Unit;
import org.lemsml.jlems.type.dynamics.DerivedVariable;
import org.lemsml.jlems.type.dynamics.Dynamics;
import org.lemsml.jlems.type.dynamics.OnStart;
import org.lemsml.jlems.type.dynamics.StateAssignment;
import org.lemsml.jlems.type.dynamics.StateVariable;
import org.lemsml.jlems.type.dynamics.TimeDerivative;
import org.lemsml.jlems.sim.ContentError;
import org.lemsml.jlems.logging.E;
import org.lemsml.jlemsio.reader.FileInclusionReader;
import org.lemsml.jlemsio.util.FileUtil;

import org.neuroml.export.base.*;

public class XppWriter extends BaseWriter {


        public XppWriter(Lems lems)
        {
                super(lems);
        }

        @Override
        protected void addComment(StringBuilder sb, String comment) {
                
                String comm = "# ";
                sb.append(comm+comment.replaceAll("\n", "\n# ")+"\n");
        }


	

	public String getMainScript() throws ContentError, ParseError {
		StringBuilder sb = new StringBuilder();
                addComment(sb,"XPP export for:\n\n"+lems.textSummary(false, false));

                Target dr = lems.getTarget();

                Component simSetCpt = dr.getComponent();
                E.info("simSetCpt: "+simSetCpt.getAllChildren());
                Component simCpt = null;
                for (Component c : simSetCpt.getAllChildren()) {
                	if (c.getTypeName().equals("Simulation"))
                		simCpt = c;
                }
                E.info("simCpt: "+simCpt);
                String targetId = simCpt.getStringValue("target");
                
                Component tgtNet = lems.getComponent(targetId);
                addComment(sb,"Adding simulation "+simCpt+" of network: "+tgtNet.summary());

                ArrayList<Component> pops = tgtNet.getChildrenAL("populations");

                for(Component pop: pops) {
                    String compRef = pop.getStringValue("component");
                    Component popComp = lems.getComponent(compRef);
                    addComment(sb,"   Population "+pop.getID()+" contains components of: "+popComp+" ");

                    String prefix = popComp.getID()+"_";

                    CompInfo compInfo = new CompInfo();
                    ArrayList<String> stateVars = new ArrayList<String>();

                    getCompEqns(compInfo, popComp, pop.getID(), stateVars, "");


                    ///sb.append(prefix+"eqs=Equations('''\n");
                    sb.append(compInfo.eqns.toString());
                    ///sb.append("''')\n\n");

                    sb.append("\n"+compInfo.params.toString());
                    String flags = "";//,implicit=True, freeze=True

                    /////sb.append(pop.getID()+" = NeuronGroup("+pop.getStringValue("size")+", model="+prefix+"eqs"+flags+")\n");
                    

                    sb.append(compInfo.initInfo.toString());
                }

                StringBuilder toTrace = new StringBuilder();
                StringBuilder toPlot = new StringBuilder();

                for(Component dispComp: simCpt.getAllChildren()){
                        if(dispComp.getName().indexOf("Display")>=0){
                                toTrace.append("# Display: "+dispComp+"\n");
                                for(Component lineComp: dispComp.getAllChildren()){
                                        if(lineComp.getName().indexOf("Line")>=0){
                                                //trace=StateMonitor(hhpop,'v',record=[0])
                                                String trace = "trace_"+lineComp.getID();
                                                String ref = lineComp.getStringValue("quantity");
                                                String pop = ref.split("/")[0].split("\\[")[0];
                                                String num = ref.split("\\[")[1].split("\\]")[0];
                                                String var = ref.split("/")[1];

                                                //if (var.equals("v")){

                                                        toTrace.append(trace+" = StateMonitor("+pop+",'"+var+"',record=["+num+"]) # "+lineComp.summary()+"\n");
                                                        toPlot.append("plot("+trace+".times/second,"+trace+"["+num+"])\n");
                                                //}
                                        }
                                }
                        }
                }
                //////sb.append(toTrace);

                float len = (float)simCpt.getParamValue("length").value;
                float dt = (float)simCpt.getParamValue("step").value;
                
                
                //if (dt.endsWith("s")) dt=dt.substring(0, dt.length()-1)+"*second";  //TODO: Fix!!!

                sb.append("@ total="+len+",dt="+dt+",maxstor=10000\n");
                sb.append("@ xhi="+len+",yhi=1.5,ylo=-1.5,nplot=2,yp2=W\n");


		return sb.toString();
	}


/*
        private String getBrianUnits(String siDim)
        {
                if(siDim.equals("voltage")) return "V";
                if(siDim.equals("conductance")) return "S";
                return null;
        }*/


        public void getCompEqns(CompInfo compInfo, Component comp, String popName, ArrayList<String> stateVars, String prefix) throws ContentError, ParseError
        {
                LemsCollection<Parameter> ps = comp.getComponentType().getDimParams();

                String localPrefix = comp.getID()+"_";

                if (comp.getID()==null)
                        localPrefix = comp.getName()+"_";

                for(Parameter p: ps)
                {
                        ParamValue pv = comp.getParamValue(p.getName());
                        //////////////String units = "*"+getBrianUnits(pv.getDimensionName());
                        String units = "";
                        if (units.indexOf(Unit.NO_UNIT)>=0)
                                units = "";
                        compInfo.params.append("par "+prefix+p.getName()+"="+(float)pv.getDoubleValue()+units+" \n");
                }
                
                if (ps.size()>0)
                        compInfo.params.append("\n");
                
                Dynamics dyn = comp.getComponentType().getDynamics();

                LemsCollection<TimeDerivative> tds = dyn.getTimeDerivatives();

                for(TimeDerivative td: tds)
                {
                        String localName = prefix+td.getVariable();
                        stateVars.add(localName);
                        //String expr = ((DVal)td.getRateexp().getRoot()).toString(prefix, stateVars);
                        String expr = td.getValueExpression();
                        expr = expr.replace("^", "**");
                        compInfo.eqns.append(""+localName+"' = "+expr+"\n");
                }

                for(StateVariable svar: dyn.getStateVariables())
                {
                        String localName = prefix+svar.getName();
                        if (!stateVars.contains(localName)) // i.e. no TimeDerivative of StateVariable
                        {
                                stateVars.add(localName);
                                compInfo.eqns.append(""+localName+"' = 0\n");
                        }
                }

/*
                ArrayList<PathDerivedVariable> pathDevVar = dyn.getPathderiveds();
                for(PathDerivedVariable pdv: pathDevVar)
                {
                        String path = pdv.getPath();
                        String bits[] = pdv.getBits();
                        StringBuilder info = new StringBuilder("# "+path +" (");
                        for (String bit: bits)
                                info.append(bit+", ");
                        info.append("), simple: "+pdv.isSimple());

                        String right = "";
                        
                        if (pdv.isSimple())
                        {
                                Component parentComp = comp;
                                for(int i=0;i<bits.length-1;i++){
                                        String type = bits[i];
                                        Component child = parentComp.getChild(type);
                                        if (child.getID()!=null)
                                                right=right+prefix+child.getID()+"_";
                                        else
                                                right=right+prefix+child.getName()+"_";
                                }
                                right=right+bits[bits.length-1];
                        } else {
                                Component parentComp = comp;
                                ArrayList<String> found = new ArrayList<String>();
                                //String ref = "";
                                for(int i=0;i<bits.length-1;i++){
                                        String type = bits[i];
                                        E.info("Getting children of "+comp+" of type "+type);
                                        ArrayList<Component>  children = parentComp.getChildrenAL(type);
                                        if (children!=null && children.size()>0){

                                                for(Component child: children){
                                                        E.info("child: "+ child);
                                                        if (child.getID()!=null)
                                                                found.add(child.getID());
                                                        else
                                                                found.add(child.getName());
                                                }
                                        }
                                }
                                for(String el: found){
                                        if (!found.get(0).equals(el))
                                                right=right+" + ";
                                        right=right+prefix+el+"_"+bits[bits.length-1];
                                }
                                if (found.isEmpty())
                                        right = "0";
                                E.info("found: "+found);

                        }
                        String line = prefix+pdv.getVariableName()+" = "+right+" : 1        # "+info;
                        E.info("line: "+line);

                        compInfo.eqns.append(line+"\n");
                }*/
                
                LemsCollection<DerivedVariable> expDevVar = dyn.getDerivedVariables();
                for(DerivedVariable edv: expDevVar)
                {
                        //String expr = ((DVal)edv.getRateexp().getRoot()).toString(prefix, stateVars);
                		String expr = edv.getFunc();
                		expr = expr.replace("^", "**");
                        compInfo.eqns.append(prefix+edv.getValue()+" = "+expr+" : 1\n");
                }

                LemsCollection<OnStart> initBlocks = dyn.getOnStarts();
                
                for(OnStart os: initBlocks)
                {
                        LemsCollection<StateAssignment> assigs = os.getStateAssignments();
                        
                        for(StateAssignment va: assigs)
                        {
                                compInfo.initInfo.append(popName+"."+prefix+va.getStateVariable().getName()+" = "+va.getValueExpression()+"\n");
                        }
                }

                for(Component child: comp.getAllChildren()) {
                        String childPre = child.getID()+"_";
                        if (child.getID()==null)
                                childPre = child.getName()+"_";
                        
                        getCompEqns(compInfo, child, popName, stateVars, prefix+childPre);
                }

		return;

        }

    public static void main(String[] args) throws Exception
    {
    	File exampleSrc = new File("/home/padraig/NeuroML2/NeuroML2CoreTypes");
    	File nml2DefSrc = new File("/home/padraig/NeuroML2/NeuroML2CoreTypes");
        //Note: only works with this example at the moment!!
    	
        File xml = new File(exampleSrc, "LEMS_NML2_Ex9_FN.xml");
        File odeFile = new File("/home/padraig/NeuroML2/NeuroML2CoreTypes/LEMS_NML2_Ex9_FN.ode");



        FileInclusionReader fir = new FileInclusionReader(xml);
        fir.addSearchPaths(nml2DefSrc.getAbsolutePath());
        Sim sim = new Sim(fir.read());
            
        sim.readModel();
		Lems lems = sim.getLems();

        XppWriter xppw = new XppWriter(lems);
        String ode = xppw.getMainScript();

        System.out.println(ode);
                    
        FileUtil.writeStringToFile(ode, odeFile);

        System.out.println("Written to: "+odeFile.getAbsolutePath());


    }


}


