# CallActivitTests

This example shows how two inform an Parent Activiti Process of some intermediate result of a child process. 
The parent may continue with the processing after it has received the message from the child.
Also the child may to something more after it has informed the parent of the intermediate result.

The scenario is the following:
- a Parent process is started and delegates execution to the child and needs some result from the child. The parent will for example send 
the result from the client to an external system by which request the process was started in the first place
- the child creates the result but has to do more things of which the parent is not really interested. 
- after the parent sent the response it waits for the child to complete before it will complete itself.

Parent Process
![Parent Process][Parent]

Child process
![Child Process][Child]

[Parent]: https://github.com/thuri/CallActivitTests/blob/master/CallActivitiTests/pictures/parent.PNG
[Child]: https://github.com/thuri/CallActivitTests/blob/master/CallActivitiTests/pictures/Child.PNG
