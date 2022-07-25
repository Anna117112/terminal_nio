package com.geekbrains;
//реализовать простую версию линуксового терминала.
//ls - список файлов в текущей директории на сервере
//        cat file - вывести на экран содержание файла с именем в текущей директории
//        cd path - перейти в папку с именем
//        Внимательно с исключениями, клиент должен понимать что не так если произошла ошибка
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Server {

    private ServerSocketChannel server;
    private Selector selector;
    private Path path;

    public Server() throws IOException {
        server = ServerSocketChannel.open();
        selector = Selector.open();
        server.bind(new InetSocketAddress(8189));
        // ассинхронный режим чтения
        server.configureBlocking(false);
        // регистрируем сервер на селекторе
        server.register(selector, SelectionKey.OP_ACCEPT);

    }

    public void start() throws IOException {
        // пока сервер открыт
        while (server.isOpen()) {
            selector.select();
            // последовательно обрабатывет все ключи потом надо удалить пройдясь итератором
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                // получили ключ
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                    // обрабатываем подключение

                }
                //
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
                // читаем сообщения

            }
        }
    }
    private void handleRead(SelectionKey key) throws IOException {
        // заведем буфер
        ByteBuffer buf = ByteBuffer.allocate(1024);
        SocketChannel channel = (SocketChannel) key.channel();
        StringBuilder s = new StringBuilder();
        ArrayList<String> arrayList = new ArrayList<>();
        // прописываем путь к файлу
        Path path = (Paths.get("cd.txt"));
        // если файл не существует то зоздаем его за ваременного хранения введенной дирректории и потом перехода в нее через метод
        if (!Files.exists(path)) {
            Files.createFile(path);
        }

        while (channel.isOpen()) {
            int read = channel.read(buf);
            // если прояитано меньше 0 то разрыв соеденения и закрываем канал

            if (read < 0) {
                channel.close();
                return;
            }
            if (read == 0) {
                break;
            }
            // записали в буфер и переключили на чтение
            buf.flip();
            // вычитываем из буфера
            while (buf.hasRemaining()) {
                s.append((char) buf.get());
            }
            buf.clear();
        }
        // записываем в сторку то что прочитали
        String st =  s.toString();
        // делем по пробелу
        String [] st1 = st.split(" ");
        // метод перебират введенный текст и ищит в нем команды и заисывает их в массив
        comand(s, arrayList);
        // проходим циклом по массиву
        for (String str : arrayList) {
            if (str.equals("ls")) {
                comandLs(str, channel,path);
            } else if (str.equals("cd")) {
                commandcd(s,st1,path);
            }
            else if (str.equals("cat")){
                commandCat(s,st1);
            }


            }
        s.append("-> ");
              byte[] message = s.toString().getBytes(StandardCharsets.UTF_8);
        System.out.println(message);
        // отправляем сообщение
           channel.write(ByteBuffer.wrap(message));

        }

// метод выводит все файлы данной дирректории
    private void comandLs(String str, SocketChannel channel, Path pathls) throws IOException {
        StringBuilder ls = new StringBuilder();
// если файл пустой то выводим файлы из текущей папки
        String strpath = Files.readString(pathls);
        if (strpath.length() < 1) {

            strpath = ".";
        }
// проходим по всем файлам и выводим их
        Files.walkFileTree(Path.of(strpath), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                System.out.println("Все папки данной дирректории  " + file.getFileName());
                ls.append(file.getFileName());

                ls.append("/");

                // обходит все если поставить Terminate то остановится когда найдет нужный
                return FileVisitResult.CONTINUE;


            }
        });
        ls.append("\n-> ");
        byte[] message = ls.toString().getBytes(StandardCharsets.UTF_8);
        channel.write(ByteBuffer.wrap(message));
    }

// ищем среди введенного команду
    private void comand(StringBuilder stringB, ArrayList<String> list) {
        String st =  stringB.toString();

        String [] st1 = st.split(" ");
        StringBuilder stringBuilder = new StringBuilder();
        // работаю на винде и терминал линукса делала через Putty для подключения через телнет
        // а при подключении сначала шлет пакет символов (data) состоит из 2х частей деленнызпо пробелу а потом записывает текст
        // поэтому полученный текст делили по пробелу во второй части st1[1] содержитсясимволы и введенна команда
        // чтобы виделить команду отсчитываем символы и берем последие 2 символа это и есть наша команда на ls b cd
        if ((st1.length > 1) &&(st1[1].length() ==16)) {
            stringBuilder.append(st1[1]);

                list.add(stringBuilder.substring(15, 17));
                // берем последние 3 символа для командв cat
            } else if ((st1.length > 1) && (st1[1].length() == 18)) {
            stringBuilder.append(st1[1]);
                list.add(stringBuilder.substring(15, 18));
            }
// если это не парвая команда после подклбчение то чисаем в обычном режиме и делим по пробелу берем первое слово
        else {
            String [] comls = st.split(" ");
            if (comls.length>1) {
                list.add(comls[0]);
            }
            else {
                String [] comcd = st.split("\r");
                list.add(comcd[0]);

            }

        }
    }



// команда перехода в дирректорию

    public void commandcd (StringBuilder string,String [] str3, Path pathcd) throws IOException {
// если первое слово это команда то берем второе слово и отделяем от него служебыне команды чтобы получить ввденное название
        if  (str3[0].equals("cd") && str3.length>1) {
            String[] dir = str3[1].split("\r");
            Path pathcd1 = Paths.get(dir[0]);
            // если это папка и она существует
            if (Files.isDirectory(pathcd1) && Files.exists(pathcd1)) {
                string.append("вы перешли в папку :");
                // записывает путь до нее
                string.append(pathcd1.toAbsolutePath().toString());
                // записывает в файл прочитанную строку (если в файле уще есть запись перетираем ее командой CREATE
                byte[] data1 = dir[0].getBytes(StandardCharsets.UTF_8);
                Files.write(pathcd, data1, StandardOpenOption.CREATE);

            } else {
                string.append("вы указали несуществующую папку");
            }
        }
        // если сначала идет пачка байтов идт при введении первого слова при подключении

        else if (!str3[0].equals("cd") && str3.length>1) {
            String[] dir = str3[2].split("\r");
            Path pathcd1 = Paths.get(dir[0]);
            if (Files.isDirectory(pathcd1) && Files.exists(pathcd1)) {
                string.append("вы перешли в папку :");
                string.append(pathcd1.toAbsolutePath().toString());
                // записывает в файл строку
                byte[] data1 = dir[0].getBytes(StandardCharsets.UTF_8);
                Files.write(pathcd, data1, StandardOpenOption.CREATE);

            } else {
                string.append("вы указали несуществующую папку");
            }
        }
        else {

            string.append("вы не указали папку");

        }


        }
    public void commandCat (StringBuilder string,String [] str3) throws IOException {

        if (str3[0].equals("cat") && str3.length > 1) {
            String[] dir = str3[1].split("\r");
            Path pathnamefile = Paths.get(dir[0]);
            // если есть право на чтение
            if (Files.isReadable(pathnamefile)) {
                // если файл существует
                if (Files.exists(pathnamefile)) {
                    string.append("Содержимое файла :");
                    string.append(pathnamefile);
                    // записывает в файл строку
                    List<String> lines = Files.readAllLines(pathnamefile);

                    for (String s : lines) {
                        string.append(s);

                    }
                } else {
                    string.append("файл не существует ");

                }


            } else {
                string.append("у фас нет прав доступа на чтение файла ");
            }

        } else if (!str3[0].equals("cat") && str3.length > 1) {
            String[] dir = str3[2].split("\r");
            Path pathname = Paths.get(dir[0]);
            if ( Files.isReadable(pathname)) {
                if (Files.exists(pathname)){
                string.append("Содержимое файла :");
                string.append(pathname);
                // записывает в файл строку
                List<String> lines = Files.readAllLines(Paths.get(String.valueOf(path)));

                for (String s : lines) {
                    string.append(s);

                }
            } else {
                string.append("файл не существует ");
                }
            }
                else{
                    string.append("у вас нет прав доступа к файлу ");

                }
            }
        else {
            string.append("Вы не указали файл");
        }

        }


// рассылка по всем если подключено несколько терминалов
        private void broadcastMessage (String msg) throws IOException {
        ByteBuffer msgBuf = ByteBuffer.wrap(msg.getBytes());
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                SocketChannel sch = (SocketChannel) key.channel();
                sch.write(msgBuf);
                msgBuf.rewind();
            }
        }
    }

        private void handleAccept () throws IOException {
        SocketChannel channel = server.accept();
        channel.configureBlocking(false);
        // регистрируем этот канал на селекторе и регистрируем операцию чтения
        channel.register(selector, SelectionKey.OP_READ);
        //при регистрации шлем в канал сообщение

        channel.write(ByteBuffer.wrap("Welcome in Anna terminal!\n -> ".getBytes(StandardCharsets.UTF_8)));

    }

}

