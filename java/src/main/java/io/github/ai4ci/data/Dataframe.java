package io.github.ai4ci.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVPrinter;


/**
 * A dataframe columnar data implementation that can handle nested dataframes.
 */
public class Dataframe extends LinkedHashMap<String, Dataframe.Column<?>> implements Iterable<Dataframe.Row>{

	public Dataframe unnest() {
		Column<Dataframe> out = newColumn(Dataframe.class);
		for (Row r:this) {
			out.add(r.unnest());
		}
		return mergeRows(out);
	}
	
	public static class Row extends LinkedHashMap<String, Dataframe.Value<?>> {
		public Row(Dataframe df, int index) {
			super();
			df.entrySet().forEach((kv) -> {
				Value<?> val = Value.of(kv.getValue().get(index));
				this.put(kv.getKey(), val);
			});
		}
		
		public Dataframe unnest() {
			int size = this.values().stream().mapToInt(v -> v.rows()).max().getAsInt();
			Dataframe out = new Dataframe();
			this.forEach((k,v) -> {
				if (v.rows() == 1) {
					// normal values:
					out.put(k, v.rep(size));
				} else {
					// dataframe values
					Dataframe nested = ((Dataframe) v.getItem());
					nested.forEach((k2,v2) -> {
						out.put(k+"."+k2, v2);
					});
				}
			});
			return out;
		}
		
		public List<Object> get(String... columns) {
			List<Object> out = new ArrayList<Object>();
			for (String col: columns) {
				Value<?> tmp=this.get(col);
				out.add(tmp == null ? null : tmp.getItem());
			}
			return out;
		}
	}
	
	/**
	 * A wrapper for items in a dataframe row, which has a concrete
	 * class field.
	 * @param <X> The type of the item
	 */
	public static class Value<X> {
		Class<X> type;
		X item;
		@SuppressWarnings("unchecked")
		public Value(X value) {this.item = value; this.type = (Class<X>) value.getClass();}
		public Class<X> getType() {return type;}
		public X getItem() {return item;}
		public int rows() {
			if (type.equals(Dataframe.class)) {
				return ((Dataframe) item).rows();
			} else {
				return 1;
			}
		}
		public static <X1> Value<X1> of(X1 value) {return new Value<X1>(value);}
		public Column<X> rep(int size) {
			Column<X> out = newColumn(type);
			for (int i=0; i<size; i++) out.add(item);
			return out;
		}
	}
	
	
	/**
	 * Columnal list storage class	
	 * @param <X> the type of the column.
	 */
	public static abstract class Column<X> extends ArrayList<X> {
		public abstract Class<X> getType();
		public boolean add(Object o) {
			if (this.getType().isAssignableFrom(o.getClass())) {
				return super.add((X) this.getType().cast(o));
			} else {
				return false;
			}
		}
	}
	
	private static <X1> Column<X1> newColumn(Class<X1> ofType) {
		return new Column<X1>() {
			@Override
			public Class<X1> getType() {
				return ofType;
			}
		};
	}
	
	private static <X1> Column<X1> newColumn(Class<X1> ofType, Collection<X1> data) {
		Column<X1> tmp = newColumn(ofType);
		tmp.addAll(data);
		return tmp;
	}
	
	@SuppressWarnings("unchecked")
	private static <X1> Column<X1> newListColumn(Collection<X1> data) {
		if (data.size() == 0) return (Column<X1>) newColumn(Object.class);
		Object first = data.iterator().next();
		if (first == null) return  (Column<X1>) newColumn(Object.class);
		Column<X1> tmp = newColumn((Class<X1>) first.getClass(), data);
		return tmp;
	}
	
	private static <X1> Column<X1> newColumn(X1 data) {
		@SuppressWarnings("unchecked")
		Column<X1> tmp = (Column<X1>) newColumn(data.getClass());
		tmp.add(data);
		return tmp;
	}
	
	private static Column<?> newColumnChecked(Object data) {
		if (data == null) return newColumn(Void.TYPE);
		if (data instanceof Stream) {
			return newColumnChecked(((Stream<?>) data).collect(Collectors.toList()));
		}
		if (data instanceof Collection) {
			if (((Collection<?>) data).isEmpty()) return newColumn(Void.TYPE);
			return newListColumn((Collection<?>) data);
		} else {
			return newColumn(data);
		}
	}
	
	
	
	
	// TODO: zero length inputs, null inputs
	private static <X> Column<X> rep(X in, int len) {
		Column<X> out = newColumn(in);
		if (out.size() == 0) return out;
		for (int i=1; i<len; i++) {
			out.add(in);
		}
		return out;
	}
	
	private static Dataframe makeSquare(Dataframe input, boolean padEmpty) {
		int[] sizes = input.values().stream().mapToInt(v -> v.size()).distinct().toArray();
		if (sizes.length == 1) return input;
		if (sizes.length > 2) throw new RuntimeException("Columns cannot be recycled, lengths are: "+Arrays.toString(sizes));
		if (!Arrays.stream(sizes).anyMatch(i -> i==1)) throw new RuntimeException("Columns cannot be recycled, lengths are: "+Arrays.toString(sizes));
		int resizeTo = Arrays.stream(sizes).max().getAsInt();
		if (!padEmpty && Arrays.stream(sizes).anyMatch(i -> i==0)) {
			input.forEach((k,v) -> v.clear());
			return input;
		}
		Dataframe output = new Dataframe();
		for (Map.Entry<String, Dataframe.Column<?>> col: input.entrySet()) {
			if (padEmpty && col.getValue().size() == 0) {
				output.put(col.getKey(), rep(null,resizeTo));
			} else if (col.getValue().size() == 1) {
				output.put(col.getKey(), rep(col.getValue().get(0),resizeTo));
			} else {
				output.put(col.getKey(), col.getValue());
			}
		}
		int[] sizes2 = output.values().stream().mapToInt(v -> v.size()).distinct().toArray();
		if (sizes2.length == 1) return output;
		throw new RuntimeException("Columns cannot be recycled, lengths are: "+Arrays.toString(sizes));
	}
	
//	private static Dataframe mergeColumns(Dataframe lhs, Dataframe rhs) {
//		Dataframe tmp = makeSquare(lhs);
//		tmp.putAll(makeSquare(rhs));
//		return makeSquare(tmp);
//	}
	
	private static Dataframe mergeRows(Column<Dataframe> input) {
		Dataframe tmp = new Dataframe();
		if (input.size() == 0) return tmp;
		input.get(1).forEach((k,col) -> tmp.put(k, newColumn(col.getType())));
		for (Dataframe nested: input) {
			tmp.forEach((k,col) -> {
				if (!nested.containsKey(k)) throw new RuntimeException("Inconsistent columns: missing "+k);
				nested.get(k).forEach(v -> col.add(v));
				
			});
		}
		return tmp;
	}
	
	public static class MapSpecification<X> extends ArrayList<MapTarget<X,?>> {
		
		Class<X> type;
		@SafeVarargs
		public MapSpecification(Class<X> input, MapTarget<X,?>... rules) {
			super();
			this.type = input;
			this.addAll(Arrays.asList(rules));
		}
		
		public Dataframe extract(X data) {
			Dataframe output = new Dataframe();
			for (MapTarget<X,?> rule: this) {
				if (rule instanceof NestedRule) {
					NestedRule<X,?> tmp = (NestedRule<X,?>) rule;
					
					Object newData = tmp.fn.apply(data);
					// This deals with the possibility of a non list output from fn
					// newList is not saved in the output
					Column<?> newList = newColumnChecked(newData);
					Column<Dataframe> newMapList = newColumn(Dataframe.class); 
					for (Object o : newList) {
						Dataframe nested = tmp.extractNested(o);
						newMapList.add(nested);
					}
					// put nested dataframes in as a nested list column
					output.put(tmp.name, newMapList);
					
				} else if (rule instanceof MapRule) {
					
					MapRule<X,?> tmp = (MapRule<X,?>) rule;
					Object newData = tmp.fn.apply(data);
					// This deals with the possibility of a non list output from fn
					// newList is saved in the output as not nested
					Column<?> newList = newColumnChecked(newData);
					output.put(tmp.name, newList);
				}
			}
			// if the provided rules result in a mix of singletons or lists then
			// this should recycle the length one lists to be the same as the
			// rest of the lists.
			// If any returned values are empty the whole dataframe is empty
			return makeSquare(output, false);
		}

		public List<String> getNames() {
			return this.stream().map(mt -> mt.getName()).collect(Collectors.toList());
		}
		
		public List<String> getKeys() {
			return this.stream().flatMap(mt -> {
				List<String> tmp = mt.getNestedNames();
				if (tmp.isEmpty()) return Stream.of(mt.getName());
				return tmp.stream().map(s -> mt.getName()+"."+s);
			}).collect(Collectors.toList());
		}
	}
	
	/**
	 * Basically a closed interface of MapRule and NestedRule to allow
	 * a hierarchical mapping specification
	 * @param <X>
	 * @param <Y>
	 */
	public static interface MapTarget<X,Y> {
		String getName();
		List<String> getNestedNames();
	}
	
	/**
	 * Create a map specification for extracting columnar data from an 
	 * object graph. The map specification provides a way to take an input and apply a set
	 * of data accessor functions to it.
	 * @param <X1> The type of the input
	 * @param type
	 * @param rules
	 * @return
	 */
	@SafeVarargs
	public static <X1> MapSpecification<X1> map(Class<X1> type, MapTarget<X1,?>... rules) {
		return new MapSpecification<X1>(type, rules);
	}
	
	/**
	 * Add a mapping to extract a column from a stream of 
	 * @param <X1> the input type
	 * @param <Y1> the output / column type
	 * @param name the name of the column
	 * @param fn a mapping lambda
	 * @return a mapping rule
	 */
	public static <X1,Y1> MapRule<X1,Y1> pull(String name, Function<X1,Y1> fn) {
		return new MapRule<X1,Y1>(name,fn);
	}
	
	
	
	/**
	 * Add a mapping to extract a column from a stream of X1 obects 
	 * @param <X1> the input type
	 * @param <Y1> the output / column type
	 * @param name the name of the column
	 * @param fn a mapping lambda
	 * @return a mapping rule
	 */
	public static <X1,Y1> MapRule<X1,Y1> pull(String name, Class<X1> type, Function<X1,Y1> fn) {
		return new MapRule<X1,Y1>(name,fn);
	}
	
	@SafeVarargs
	public static <X1,Y1> NestedRule<X1,Y1> nest(String name, Function<X1,Y1> fn, Class<Y1> type, MapTarget<Y1,?>... rules) {
		return new NestedRule<X1,Y1>(name, fn, type, rules);
	}
	
	/**
	 * Can use this to nest some data when there are multiple columns with 
	 * different lengths. 
	 * @param <X1> the input type
	 * @param name the name of the nested columns
	 * @param type the input type
	 * @param rules the mappings for the nested dataframe.
	 * @return a mapping specification
	 */
	@SafeVarargs
	public static <X1> NestedRule<X1,X1> nest(String name, Class<X1> type, MapTarget<X1,?>... rules) {
		return new NestedRule<X1,X1>(name, x -> x, type, rules);
	}
	
	public static class MapRule<X,Y> implements MapTarget<X,Y> {
		String name;
		Function<X,Y> fn;
		public MapRule(String k, Function<X, Y> v) {
			this.name = k;
			this.fn = v;
		}
		public String getName() {return name;}
		public List<String> getNestedNames() {return Collections.emptyList();}
	}
	
	public static class NestedRule<X,Y> implements MapTarget<X,Y> {
		String name;
		Function<X, Y> fn;
		Class<Y> type;
		MapSpecification<Y> nested;
		@SafeVarargs
		public NestedRule(String name, Function<X, Y> fn, Class<Y> type, MapTarget<Y,?>... rules) {
			this.name = name;
			this.fn = fn;
			this.type = type;
			this.nested = new MapSpecification<Y>(type, rules);
		}
		public Dataframe extractNested(Object data) {
			return nested.extract(type.cast(data));
		}
		public String getName() {return name;}
		public List<String> getNestedNames() {return nested.getNames();}
	}

	public int rows() {
		int[] sizes = this.values().stream().mapToInt(v -> v.size()).distinct().toArray();
		if (sizes.length != 1) throw new RuntimeException("inconsistent shape dataframe");
		return sizes[0];
	}
	
	@Override
	public Iterator<Row> iterator() {
		return new Iterator<Row>() {
			int i = 0;
			@Override
			public boolean hasNext() {
				return i < rows();
			}
			@Override
			public Row next() {
				Row tmp = new Row(Dataframe.this, i);
				i +=1 ;
				return tmp;
			}
		};
	}
	
	public void writeCsv(CSVPrinter out, String... columns) throws IOException {
		for (Row row:this) {
			out.printRecord(row.get(columns));
		}
	}
	
}
