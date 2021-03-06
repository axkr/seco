package seco.storage.swing.types;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.swing.plaf.BorderUIResource;

import org.hypergraphdb.HGException;
import org.hypergraphdb.HGHandle;
import org.hypergraphdb.HGLink;
import org.hypergraphdb.HGPersistentHandle;
import org.hypergraphdb.HGPlainLink;
import org.hypergraphdb.HGTypeSystem;
import org.hypergraphdb.HGQuery.hg;
import org.hypergraphdb.annotation.AtomReference;
import org.hypergraphdb.atom.AtomProjection;
import org.hypergraphdb.atom.HGAtomRef;
import org.hypergraphdb.atom.HGRelType;
import org.hypergraphdb.type.AtomRefType;
import org.hypergraphdb.type.BonesOfBeans;
import org.hypergraphdb.type.HGAtomType;
import org.hypergraphdb.type.JavaTypeFactory;
import org.hypergraphdb.type.Record;
import org.hypergraphdb.type.RecordType;
import org.hypergraphdb.type.Slot;
import org.hypergraphdb.type.TypeUtils;

import seco.storage.swing.DefaultConverter;
import seco.storage.swing.MetaData;
import seco.storage.swing.RefUtils;
import seco.storage.swing.DefaultConverter.AddOnType;
import seco.talk.ConnectionPanel;

public class SwingType extends RecordType
{
	private Class<?> javaClass;
	private HGHandle ctrHandle;
	private HGHandle addOnsHandle;
	protected HashMap<String, HGHandle> slotHandles = new HashMap<String, HGHandle>();


	public SwingType(Class<?> javaClass)
	{
		this.javaClass = javaClass;
	}

	public void init(HGHandle typeHandle)
	{
		this.thisHandle = typeHandle;
		DefaultConverter c = MetaData.getConverter(javaClass);
		createSlots(c);
		ctrHandle = createCtrLink(c);
		addOnsHandle = createAddonsLink(c);
	}

	public Class<?> getJavaClass()
	{
		return javaClass;
	}

	public void release(HGPersistentHandle handle)
	{
		graph.getStore().removeLink(handle);
	}

	private HGHandle createCtrLink(DefaultConverter cin)
	{
		if (!(cin instanceof DefaultConverter))
			return graph.getHandleFactory().nullHandle();
		DefaultConverter c = (DefaultConverter) cin;
		if (c.getCtr() == null && c.getFactoryCtr() == null)
			return graph.getHandleFactory().nullHandle();
		String[] args = c.getCtrArgs();
		Class<?>[] types = c.getCtrTypes();
		if (c.getFactoryCtr() != null)
		{
			HGHandle[] targets = new HGHandle[args.length + 2];
			Class<?> declaring = c.getFactoryCtr().getDeclaringClass();
			targets[0] = hg.addUnique(graph, 
			                          declaring, 
			                          hg.and(hg.type(Class.class),
			                                 hg.eq("name", declaring.getName()))); 
			targets[1] = graph.add(c.getFactoryCtr().getName());
			for (int i = 0; i < types.length; i++)
			{
				//HGHandle[] r = new HGHandle[] { slotHandles.get(args[i]) };
				targets[i + 2] = graph.add(//new HGValueLink(types[i], r));
						new HGPlainLink(graph.add(types[i]),
	  							   slotHandles.get(args[i])));
			}
			return graph.add(new FactoryConstructorLink(targets));
		}
	
		HGHandle[] targets = new HGHandle[args.length];
		for (int i = 0; i < types.length; i++)
		{
    		if(types[i] == null)
				System.err.println("CTRLink - NULL type: " + args[i] + ":" + javaClass);
			targets[i] = graph.add(new HGPlainLink(graph.add(types[i]),
					  							   slotHandles.get(args[i])));
		}
		return graph.add(new ConstructorLink(targets));
	}
	
	protected void createSlots(DefaultConverter c)
	{
		Map<String, Class<?>> slots = c.getSlots();
		//System.out.println("SwingType: " + javaClass + ":" + slots.size() + ":" + 
		//		((DefaultConverter)c).getType() + ":" + graph);
		HGTypeSystem typeSystem = graph.getTypeSystem();
		for (String s : slots.keySet())
		{
			//System.out.println("Slot: " + s);
			Class<?> propType = slots.get(s);
			if (propType == null)
				System.err.println("NULL Class for " + s + " in " + javaClass);
			if (propType.isPrimitive())
				propType = BonesOfBeans.wrapperEquivalentOf(propType);
			HGHandle valueTypeHandle = typeSystem.getTypeHandle(propType);
			HGHandle slotHandle = 
			    JavaTypeFactory.getSlotHandle(graph, s, valueTypeHandle);
			addSlot(slotHandle);
			HGAtomRef.Mode refMode = getReferenceMode(javaClass, s);
			if (refMode != null)
				typeSystem.getHyperGraph().add(
						new AtomProjection(thisHandle, s, valueTypeHandle, refMode));
		}
	}
	
	private HGAtomRef.Mode getReferenceMode(Class<?> javaClass, String field_name)
    {
	    Field field = RefUtils.getField(javaClass, field_name);
	    if(field == null) return null;
        //
        // Retrieve or recursively create a new type for the nested
        // bean.
        //
        AtomReference ann = (AtomReference)field.getAnnotation(AtomReference.class);
        if (ann == null)
            return null;
        String s = ann.value();
        if ("hard".equals(s))
            return HGAtomRef.Mode.hard;
        else if ("symbolic".equals(s))
            return HGAtomRef.Mode.symbolic;
        else if ("floating".equals(s))
            return HGAtomRef.Mode.floating;
        else
            throw new HGException("Wrong annotation value '" + s + 
                    "' for field '" + field.getName() + "' of class '" +
                    javaClass.getName() + "', must be one of \"hard\", \"symbolic\" or \"floating\".");
    }

	protected HGHandle createAddonsLink(DefaultConverter c)
	{
		Set<AddOnType> set = c.getAllAddOnFields();
		if (set == null) return graph.getHandleFactory().nullHandle();
		HGHandle[] targets = new HGHandle[set.size()];
		int i = 0;
		for (AddOnType a : set)
		{
			String[] args = a.getArgs();
			HGHandle[] t = new HGHandle[args.length];
			for (int j = 0; j < args.length; j++){
			    t[j] = slotHandles.get(args[j]);
//			    System.out.println("AddOnSlots: " + javaClass + ":"
//			            + args[j] + ":" + c);
//			    System.out.println("AddOnSlots: " + graph.get(t[j]));
			}
			HGLink link = new HGRelType(a.getName(), t);
			//System.out.println("ADDONLINK: " + javaClass + ":" + a.getName()
			//		+ ":" + args[0] + ":" + args.length);
			targets[i] = graph.add(link);
			i++;
		}
		return graph.add(new AddOnLink(targets));
	}

	public HGHandle getAddOnsHandle()
	{
		return addOnsHandle;
	}

	public HGHandle getCtrHandle()
	{
		return ctrHandle;
	}

	public void setAddOnsHandle(HGHandle addOnsHandle)
	{
		this.addOnsHandle = addOnsHandle;
	}

	public void setCtrHandle(HGHandle ctrHandle)
	{
		this.ctrHandle = ctrHandle;
	}

	public void addSlot(HGHandle slot)
	{
		if (!slots.contains(slot)) 
		{
			slots.add(slot);
			Slot s = (Slot) graph.get(slot);
			if( s!= null)
			  slotHandles.put(s.getLabel(), slot);
		}
	}

	public void remove(HGHandle slot)
	{
		int i = slots.indexOf(slot);
		if(i >= 0 && i < slots.size())
			removeAt(i);
	}

	public void removeAt(int i)
	{
		Slot s = (Slot) graph.get(slots.get(i));
		slots.remove(i);
		slotHandles.remove(s);
	}
	
//	public HGPersistentHandle store(Object instance)
//    {
//       // if (slots.isEmpty())
//       //     return graph.getHandleFactory().nullHandle();
//        HGPersistentHandle handle = TypeUtils.getNewHandleFor(graph, instance);
//        if (! (instance instanceof Record))
//            throw new HGException("RecordType.store: object is not of type Record.");
//        Record record = (Record)instance;
//        HGPersistentHandle [] layout = new HGPersistentHandle[slots.size() * 2];
//        for (int i = 0; i < slots.size(); i++)
//        {       
//            HGHandle slotHandle = getAt(i);
//            Slot slot = (Slot)graph.get(slotHandle);
//            Object value = record.get(slot);            
//            if (value == null)
//            {
//                layout[2*i] = graph.getPersistentHandle(slot.getValueType());
//                layout[2*i + 1] = graph.getHandleFactory().nullHandle();
//            }
//            else
//            {
//                HGAtomRef.Mode refMode = getReferenceMode(slotHandle);          
//                if (refMode == null)
//                {
//                    HGHandle actualTypeHandle = graph.getTypeSystem().getTypeHandle(value.getClass());
//                    if (actualTypeHandle == null)
//                        actualTypeHandle = slot.getValueType();
//                    else if (actualTypeHandle.equals(graph.getTypeSystem().getTop()))
//                        throw new HGException("Got TOP type for value for Java class " + value.getClass());
//                    HGAtomType type = graph.getTypeSystem().getType(actualTypeHandle);                
//                    layout[2*i] = graph.getPersistentHandle(actualTypeHandle);
//                    try
//                    {
//                        layout[2*i + 1] = TypeUtils.storeValue(graph, value, type);
//                    }
//                    catch (HGException ex)
//                    {
//                        throw ex;
//                    }
//                }
//                else
//                {
//                    layout[2*i] = graph.getPersistentHandle(slot.getValueType());
//                    if (value instanceof HGAtomRef)
//                    {
//                        AtomRefType refType = graph.getTypeSystem().getAtomType(HGAtomRef.class);
//                        layout[2*i + 1] = refType.store((HGAtomRef)value);
//                    }
//                    else
//                        throw new HGException("Slot " + slot.getLabel() + 
//                                              " should have an atom reference for record " + 
//                                              graph.getHandle(this));
//                }
//            }
//        }
//        if (!slots.isEmpty())
//        graph.getStore().store(handle, layout);
//        return handle;
//    }
}
