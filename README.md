# mJCompiler

A compiler for a Java-like language.
It is a statically-typed programming language with support for object-oriented and procedural programming concepts.
It includes:
- Variables, constants, and arrays
- Classes and interfaces
- Control structures (if, loops)
- Methods and function calls
- Expressions and operators
- Object-oriented programming with inheritance and polymorphism

## Features

### Program Structure
A program starts with the keyword `program`, followed by an identifier. The program consists of constant declarations, global variable declarations, class and interface definitions, and method declarations.
A program must contain a parameterless void method called `main`, which represents the program's etry point.

```
program myProgram

// Constant declarations, global variable declarations, class and interface deginitions

{
    // Method declarations
}
```

---

### Constants and Variables

Constants are declared using the `const` keyword and must be initialized at the time of declaration.

```
const int max = 100, min = 0;
const char letter = 'a';
const bool flag = true;
```


Variables are declared using a type specifier and an identifier. Arrays are supported with square brackets `[]`.

```
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

```plaintext
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

Objects, as well as arrays, are created with `new` keyword:

```
void main() int arr[]; Movable m; {
    arr = new int[10];
    m = new Block();
    m.move(arr[0], arr[1]);
}
```

---

### Methods Signatures
Methods are defined with a return type (or `void` for no return value), a name, and an optional list of local parameters.

```plaintext
void printId() {
    print('I');
    print('M');
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

The language supports *if-else* statements and *do-while* loops.
Both conditional statements and loops can be nested.
Loops support `continue` and `break` statements;

```
if (x > 10) {
    print(1);
} else if (x == 5) {
    print(2);
} else {
    print(3);
}
```

```
do {
    if (x % 2 == 0) continue;
    else print(x);

    if (x == 0) break;
} while (x < 20, x--);
```


### Built in methods

There are built in `read` and `print` methods, for reading from standard input and printing to standard output.

```
void main() int x; {
    read(x);
    print(x + 2, 5);
}
```

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

---

### Example Program

```\
program Example 
    const int max = 100;

    class Person {
        int age;

        {
            bool setAge(int number) {
                if (number < max) {
                    age = number;
                    return true;
                }

                return false;
            }
        }
    }
{
    int readNumber() int temp; {
        read(temp);
        return temp;
    }

    void main() bool res; Person p; {
        p = new Person();
        res = p.setAge(readNumber());

        if (res == false) {
            print('e'); print('r'); print('r'); print('o'); print('r');
        }
    }
}
```
