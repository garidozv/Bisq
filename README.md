# Bisq Compiler

A compiler for _MicroJava_, a Java-like programming language.
It is a statically typed programming language with support for object-oriented and procedural programming concepts.
It includes:
- Variables, constants, and arrays
- Classes and interfaces
- Control structures (if, loops)
- Methods and function calls
- Expressions and operators
- Object-oriented programming with inheritance and polymorphism
- Generic types and methods with constraints

## Features

### Program Structure
A program starts with the keyword `program`, followed by an identifier. The program consists of constant declarations, global variable declarations, class and interface definitions, and method declarations.
A program must contain a parameterless void method called `main`, which represents the program's entry point.

```java
program myProgram

// Constant declarations, global variable declarations, class and interface definitions

{
    // Method declarations
}
```

---

### Constants and Variables

Constants are declared using the `const` keyword and must be initialized at the time of declaration.

```java
const int max = 100, min = 0;
const char letter = 'a';
const bool flag = true;
```


Variables are declared using a type specifier and an identifier. Arrays are supported with square brackets `[]`.

```java
int x, y, z;
bool isActive;
char letter;
int num, numbers[];
```

---

### Classes and Interfaces

A class can extend another class or implement an interface and contains variable declarations and methods.
Base class methods can be overridden.
Interfaces can contain concrete methods or method signatures without implementation.
All non-concrete interface methods must be implemented in the class that implements that interface.

```java
interface Movable {
    void move(int x, int y);
}

class Object extends Movable {

    {
        void objectMove(int x, int y) {
            // ...
        }
        void move(int x, int y) {
            customMove(x, y);
        }
}

class Block extends Object {
    int rotation;

    {
        void objectMove(int x, int y) {
            // override
        }
    }
}
```

Objects, as well as arrays and sets, are created with the `new` keyword:

```java
void main() int arr[]; Movable m; set s; {
    s = new set[20];
    arr = new int[10];
    m = new Block();
    m.move(arr[0], arr[1]);
}
```

---

### Generics

The language supports generic classes, interfaces, global methods, and member methods.  </br>
Generic parameters are declared between angle brackets `<>`, and they have to be placed after the type name or before the return type in the case of methods. <br/>
Explicit type arguments must be provided when the generics are used, this includes object creation and method calls.
For both of them, type arguments are provided after the name, the difference being that object creation
only uses angle brackets, while method calls use the _turbofish_ operator `::<>`.

```java
class Wrapper<T> {
   T value;
}

{
   <T> T getAtIndex(T array[], int index) {
       return array[index];
   }

   void main() Wrapper<char> array[]; Wrapper<char> temp; {
       array = new Wrapper<char>[2];
       array[0] = new Wrapper<char>();
       array[0].value = 'D';
       array[1] = new Wrapper<char>();
       array[1].value = 'V';

       temp = getAtIndex::<Wrapper<char>>(array, 0);
       print(temp.value);
   }
}
```
A generic parameter can have a class or interface constraint, written as `<T : Constraint>`.
A supplied type must satisfy the constraint, and the generic declaration can use the members provided by it. <br/>
If no constraint is specified, any valid type can be supplied as the type argument, and values of that type can be
stored, assigned, passed, and returned, but no class or interface members can be accessed through it.

Generic inheritance and virtual generic methods are supported. Any combination of the generic and non-generic class or
interface can establish the inheritance, and the generic methods can be overridden just like the regular methods.
```java
interface Printable {
    void printMe();
}

class Document extends Printable {
    {
        void printMe() {
            print("doc");
        }
    }
}

interface Provider<T : Printable> {
    T get();

    <U> U echo(U value) {
        return value;
    }
}

class BaseProvider<T : Printable> extends Provider<T> {
    T value;
    {
        T get() {
            return value;
        }

        <U> U echo(U value) {
            value.printMe();
            return value;
        }
    }
}

class DerivedProvider<T : Printable> extends BaseProvider<T> {}
```
---

### Method Signatures
Methods are defined with a return type (or `void` for no return value), a name, and an optional list of local parameters.

```java
void printId() {
    print("IM");
    print(124);
    print(eol);
}

int add(int a, int b) int temp; {
    temp = a + b;
    return temp;
}
```

---
### Conditional Statements

The language supports *if-else* statements, *do-while* loops, and *for* loops.
Both conditional statements and loops can be nested.
Loops support `continue` and `break` statements;

```java
if (x > 10) {
    print(1);
} else if (x == 5) {
    print(2);
} else {
    print(3);
}
```

```java
do {
    if (x % 2 == 0) continue;
    else print(x);

    if (x == 0) break;
} while (x < 20, x--);
```
```java
sum = 0;
for (i = 1; i < 10; i++) {
    sum = sum + i;
}
```


### Built-in and predefined methods

Built-in methods:
- `print(expr [, width])`
    - Prints the passed expression to the standard output
    - Optionally, you can specify width to format the output
    - String literals, with support for printing a new line via `\n`, can be printed directly with `print("...")`
- `read(var)`
    - Reads input from the standard input and assigns it to the specified variable
 
Predefined methods:
- `ord(ch)`
    - Returns the integer value for the given character
- `chr(num)`
    - Converts the specified integer into the corresponding character
- `len(arr)`
    - Returns the length of the array
- `add(s, num)`
    - Adds the number to the set
    - If the set already contains the number or if the set is full, no action is taken
- `addAll(s1, arr)`
    - Adds all elements from the specified array to the set

---

### Expressions and Operators

Arithmetic Operators:
- `+`, `-`, `*`, `/`, `%`

Comparison Operators
- `==`, `!=`, `>`, `>=`, `<`, `<=`

Logical Operators
- `&&`, `||`

Assignment Operator
- `=`

Increment and Decrement
- `--`, `++`

Set Oprators
- `union`

Ternary Operator
- `? :`

Array Operators
- `map`
    - Left operand has to be a method that takes in one integer and returns an integer
    - Right operand has to be an integer array
    - `map` calls the passed method on each element of the array, and returns the sum of returned values

---

### Example Program

```java
program ExampleProgram

const int BATCH_ID_START = 1;
int nextBatchId;

interface Printable {
    void printMe();
}

interface ProcessingQueue<TItem : Printable> {
    void handle(TItem item);

    void process(TItem items[]) int index; {
        print("Processing ");
        print(len(items));
        print(" items\n");

        for (index = 0; index < len(items); index++) {
            print(eol);
            print("Next item: ");
            items[index].printMe();
            print(eol);

            this.handle(items[index]);
        }

        print(eol);
        print("All items processed\n");
    }
}

class NumberBatch extends Printable {
    int id;
    int values[];
    int count;

    {
        void initialize(int batchId, int capacity) {
            id = batchId;
            values = new int[capacity];
            count = 0;
        }

        void add(int value) {
            values[count] = value;
            count++;
        }

        void printMe() int index; {
            print("Batch "); print(id); print(" [");
            for (index = 0; index < count; index++) {
                print(values[index]);
                if (index < count - 1) {
                    print(", ");
                }
            }
            print(']');
        }
    }
}

class AdditionQueue extends ProcessingQueue<NumberBatch> {
    int grandTotal;
    int processedBatches;

    {
        void handle(NumberBatch batch) int index, sum; {
            sum = 0;
            for (index = 0; index < batch.count; index++) {
                sum = sum + batch.values[index];
            }

            grandTotal = grandTotal + sum;
            processedBatches++;

            print("Sum: "); print(sum); print(eol);
        }

        void printSummary() {
            print(eol);
            print("Addition Queue Summary"); print(eol);
            print("Batches: "); print(processedBatches); print(eol);
            print("Total: "); print(grandTotal); print(eol);
        }
    }
}

class MaximumQueue extends ProcessingQueue<NumberBatch> {
    {
        void handle(NumberBatch batch) int index, maximum; {
            maximum = batch.values[0];
            for (index = 1; index < batch.count; index++) {
                if (batch.values[index] > maximum) {
                    maximum = batch.values[index];
                }
            }

            print("Max: "); print(maximum); print(eol);
        }
    }
}

{
    NumberBatch[] createBatches() NumberBatch batches[]; NumberBatch batch; {
        batches = new NumberBatch[3];
        nextBatchId = BATCH_ID_START;

        batch = new NumberBatch();
        batch.initialize(nextBatchId, 3);
        nextBatchId++;
        batch.add(2); batch.add(4); batch.add(6);
        batches[0] = batch;

        batch = new NumberBatch();
        batch.initialize(nextBatchId, 4);
        nextBatchId++;
        batch.add(10); batch.add(-3); batch.add(8); batch.add(5);
        batches[1] = batch;

        batch = new NumberBatch();
        batch.initialize(nextBatchId, 3);
        nextBatchId++;
        batch.add(7); batch.add(7); batch.add(1);
        batches[2] = batch;

        return batches;
    }

    void main() NumberBatch batches[]; ProcessingQueue<NumberBatch> queue;
                AdditionQueue additionQueue; MaximumQueue maximumQueue; {
        batches = createBatches();

        print("===== Addition Queue =====\n");

        additionQueue = new AdditionQueue();
        queue = additionQueue;
        queue.process(batches);
        additionQueue.printSummary();

        print(eol);
        print("===== Maximum Queue =====\n");

        maximumQueue = new MaximumQueue();
        queue = maximumQueue;
        queue.process(batches);
    }
}
```
