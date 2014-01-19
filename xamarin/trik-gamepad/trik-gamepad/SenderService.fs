module com.trik.gamepad.Transmitter
open Android.Widget
open Android.Content
open Android.App
open Android.Util
open System

[<assembly: UsesPermission("android.permission.INTERNET")>]
[<assembly: UsesPermission("android.permission.VIBRATE")>]
do()
let konst c = fun _ -> c
let private SOS = [| 0L; 50L; 50L; 50L; 50L; 50L; 
            100L; 200L; 50L; 200L; 50L; 200L;
            100L;  50L; 50L; 50L; 50L; 50L|]

type Message = Send of string | Connect of (string * int) | Shutdown

let create ()  =
    let invalidState got expected = invalidOp "%A instead of %A." got expected
    MailboxProcessor.Start <| fun input ->        
        let rec reconnect ((host, port) as target) last = async {
                try 
                    let socket = new Net.Sockets.TcpClient(host, port, NoDelay = true, SendTimeout = 5000)
                    return! transmit last (target, new IO.StreamWriter(socket.GetStream(), AutoFlush = true))
                with e ->
                    let rec loop attempts last = async { 
                        let! cmd = input.TryReceive 30
                        match cmd with
                        | None -> return! loop (attempts - 1) last
                        | Some (Connect target) -> return (target, last)
                        | Some Shutdown -> return (target, cmd) 
                        | Some (Send s) -> return! loop (attempts - 1) cmd
                    }
                    Log.Error("TCP", "Failed: {0}, Trouble : {1} ", target, e.Message) |> ignore
                    let! (target, last) = loop 30 last
                    return! reconnect target last 
                } 
        and transmit last (target,(stream:IO.StreamWriter) as arg) = async {                
                let reconnect target last = async {
                        (stream:>IDisposable).Dispose()
                        return! reconnect target last 
                        }
                let next () = async {
                    let! last = input.Receive ()
                    return! transmit (Some last) arg
                }
                

                match last with 
                    | None -> return! next()
                    | Some Shutdown -> return ()
                    | Some (Connect target) -> return! reconnect target None
                    | Some (Send s) -> 
                        try
                            stream.WriteLine s
                            return! next()
                        with
                            e -> Log.Error("TCP", "Failed: {0}, Trouble : {1} ", s, e.Message) |> ignore
                                 return! reconnect target last
            }
   
        async { 
            let! cmd = input.Receive() 
            match cmd with
                | Connect target  -> return! reconnect target None
                | Shutdown -> return ()
                | Send s ->  invalidState cmd ["Connect";"Shutdown"] 
         }
    